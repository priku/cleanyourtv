package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * The Now playing card (now_playing): the media session of an app playing in the background, right-aligned
 * just above the dock, with play/pause on OK, skip on Left/Right and the app itself on a long press.
 * It is reached only from Settings (Down) and the dock's last app (Up); Down goes back to that app. The card
 * is focusable only while it is being entered, so focus search from the other tiles and pills never lands on it.
 *
 * Android lists other apps' media sessions only to a caller with an enabled notification listener
 * ({@link NowPlayingListener}), which the install script enables when asked (NOW_PLAYING=1). Without it
 * Android refuses the session list and the card never shows. The first failure in the media session APIs switches the card off for the session
 * ({@link #broken}).
 *
 * One object does all the listening (keys, clicks, focus, layout, scrolling, session changes), so the card
 * adds as few classes to the APK as possible: the launcher has a size budget.
 */
final class NowPlayingCard extends MediaController.Callback implements Runnable, View.OnClickListener,
        View.OnLongClickListener, View.OnKeyListener, View.OnFocusChangeListener, View.OnLayoutChangeListener,
        ViewTreeObserver.OnScrollChangedListener, MediaSessionManager.OnActiveSessionsChangedListener {

    private static final float FOCUS_SCALE = 1.04f;
    /** Set by the first failure in the media session APIs: the card stays off for this session. */
    static volatile boolean broken;

    // android.media.session.PlaybackState values, repeated so the logic below stays testable on the JVM.
    static final int STATE_PAUSED = 2;
    static final int STATE_PLAYING = 3;

    /** Playing, or about to: the card shows these first and draws the pause glyph. */
    static boolean isActive(int state) {
        switch (state) {
            case STATE_PLAYING:
            case 4:    // fast forwarding
            case 5:    // rewinding
            case 6:    // buffering
            case 8:    // connecting
            case 9:    // skipping to previous
            case 10:   // skipping to next
            case 11:   // skipping to a queue item
                return true;
            default:
                return false;
        }
    }

    /**
     * The session to show, as an index into the lists Android returns (most recently active first):
     * the first one playing, else the first one paused; -1 when nothing is playing or paused. The
     * launcher's own package is skipped.
     */
    static int choose(int[] states, String[] packages, String self) {
        int paused = -1;
        for (int i = 0; i < states.length; i++) {
            if (self.equals(packages[i])) continue;
            if (isActive(states[i])) return i;
            if (states[i] == STATE_PAUSED && paused < 0) paused = i;
        }
        return paused;
    }

    /** The first line: the track's title, else the display title, else the app's name. */
    static String title(String title, String displayTitle, String appLabel) {
        if (!blank(title)) return title.trim();
        if (!blank(displayTitle)) return displayTitle.trim();
        return appLabel;
    }

    /**
     * The second line, "artist · app": the artist (or the album when there is none) and the app's
     * name, leaving out what's missing and the app's name when the first line already is it.
     */
    static String subtitle(String artist, String album, String appLabel, String shownTitle) {
        String who = !blank(artist) ? artist.trim() : !blank(album) ? album.trim() : "";
        boolean appInTitle = appLabel.equals(shownTitle);
        if (who.isEmpty()) return appInTitle ? "" : appLabel;
        return appInTitle ? who : who + " · " + appLabel;
    }

    private static boolean blank(String s) {
        return s == null || s.trim().isEmpty();
    }


    private final MainActivity home;
    private final Handler handler;
    private final FrameLayout root;
    private final RecyclerView grid;
    private final View topBar;
    private final TextView heroName;
    private final boolean animations;
    private final float density;
    private final View card;
    private final ImageView art;
    private final ImageView stateIcon;
    private final TextView title;
    private final TextView subtitle;
    private final ComponentName listener;
    private MediaSessionManager sessions;
    private final List<MediaController> watched = new ArrayList<>();
    private MediaController shown;
    private boolean visible;
    private Bitmap artSource;
    private String artPkg;

    NowPlayingCard(MainActivity home, Handler handler, FrameLayout root, RecyclerView grid, View topBar,
                   TextView heroName, boolean animations) {
        this.home = home;
        this.handler = handler;
        this.root = root;
        this.grid = grid;
        this.topBar = topBar;
        this.heroName = heroName;
        this.animations = animations;
        density = home.getResources().getDisplayMetrics().density;
        card = home.findViewById(R.id.nowPlaying);
        art = home.findViewById(R.id.nowPlayingArt);
        stateIcon = home.findViewById(R.id.nowPlayingState);
        title = home.findViewById(R.id.nowPlayingTitle);
        subtitle = home.findViewById(R.id.nowPlayingSubtitle);
        listener = new ComponentName(home, NowPlayingListener.class);
        heroName.setEllipsize(TextUtils.TruncateAt.END);
        // The pills' glass, with a tile's lift and highlight for focus.
        GradientDrawable glass = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x2EFFFFFF, 0x14FFFFFF});
        glass.setStroke(Math.round(density), 0x38FFFFFF);
        glass.setCornerRadius(16 * density);
        card.setBackground(glass);
        // Rounded artwork: the outline comes from this background, which the artwork covers.
        GradientDrawable corners = new GradientDrawable();
        corners.setCornerRadius(9 * density);
        art.setBackground(corners);
        art.setClipToOutline(true);
        card.setOnClickListener(this);
        card.setOnLongClickListener(this);
        card.setOnKeyListener(this);
        card.setOnFocusChangeListener(this);
        // The card sits in the space above the dock: keep it there as the layout settles, and out of the
        // way while the grid is scrolled up over that space.
        grid.addOnLayoutChangeListener(this);
        card.addOnLayoutChangeListener(this);
        grid.getViewTreeObserver().addOnScrollChangedListener(this);
    }

    boolean visible() {
        return visible;
    }

    /** Enters the card from the dock's last app or from Settings. */
    boolean requestFocus() {
        if (!visible) return false;
        card.setFocusable(true);
        return card.requestFocus();
    }

    void start() {
        if (broken) return;
        try {
            if (sessions == null) sessions = home.getSystemService(MediaSessionManager.class);
            sessions.addOnActiveSessionsChangedListener(this, listener, handler);
            onActiveSessionsChanged(sessions.getActiveSessions(listener));
        } catch (SecurityException notGranted) {
            // The notification listener isn't enabled (NOW_PLAYING=1 at install): no sessions to show.
            show(null);
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    void stop() {
        handler.removeCallbacks(this);
        try {
            if (sessions != null) sessions.removeOnActiveSessionsChangedListener(this);
        } catch (Exception ignored) {
            // Not registered: nothing to undo.
        }
        unwatch();
    }

    private void fail(Throwable t) {
        if (!broken) Log.w("TvHome", "now playing off", t);
        broken = true;
        show(null);
    }

    /** Follows every active session, so whichever one starts playing can take the card. */
    @Override
    public void onActiveSessionsChanged(List<MediaController> list) {
        unwatch();
        try {
            if (list != null) {
                for (MediaController c : list) {
                    c.registerCallback(this, handler);
                    watched.add(c);
                }
            }
            run();
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    private void unwatch() {
        for (MediaController c : watched) {
            try {
                c.unregisterCallback(this);
            } catch (Exception ignored) {
                // A session that died already has nothing to unregister.
            }
        }
        watched.clear();
    }

    // Callbacks come in bursts (state, then metadata): draw once after them.
    @Override
    public void onPlaybackStateChanged(PlaybackState state) {
        handler.removeCallbacks(this);
        handler.postDelayed(this, 120);
    }

    @Override
    public void onMetadataChanged(MediaMetadata metadata) {
        onPlaybackStateChanged(null);
    }

    @Override
    public void onSessionDestroyed() {
        onPlaybackStateChanged(null);
    }

    /** Picks the session to show and draws it. */
    @Override
    public void run() {
        if (broken) return;
        try {
            int n = watched.size();
            int[] states = new int[n];
            String[] packages = new String[n];
            for (int i = 0; i < n; i++) {
                PlaybackState st = watched.get(i).getPlaybackState();
                states[i] = st != null ? st.getState() : 0;
                packages[i] = watched.get(i).getPackageName();
            }
            int pick = choose(states, packages, home.getPackageName());
            if (pick >= 0) bind(watched.get(pick), states[pick]);
            show(pick >= 0 ? watched.get(pick) : null);
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    private void bind(MediaController c, int state) {
        String pkg = c.getPackageName();
        PackageManager pm = home.getPackageManager();
        String appLabel = pkg;
        try {
            appLabel = pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString();
        } catch (Exception ignored) {
            // A package we can't see keeps its package name.
        }
        MediaMetadata md = c.getMetadata();
        String line1 = title(text(md, MediaMetadata.METADATA_KEY_TITLE),
                text(md, MediaMetadata.METADATA_KEY_DISPLAY_TITLE), appLabel);
        String line2 = subtitle(text(md, MediaMetadata.METADATA_KEY_ARTIST),
                text(md, MediaMetadata.METADATA_KEY_ALBUM), appLabel, line1);
        title.setText(line1);
        subtitle.setText(line2);
        subtitle.setVisibility(line2.isEmpty() ? View.GONE : View.VISIBLE);
        card.setContentDescription(line2.isEmpty() ? line1 : line1 + ", " + line2);
        stateIcon.setImageResource(isActive(state)   // the platform's glyphs: no artwork of our own to ship
                ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play);

        Bitmap picture = md == null ? null : md.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (picture == null && md != null) picture = md.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (picture == null && md != null) picture = md.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        // Metadata comes again with every state change; scale a new picture only when it changed.
        if (picture != artSource || !pkg.equals(artPkg)) {
            if (picture != null) {
                // A centre square at the card's size, so a 1000 px cover isn't kept to be drawn at 44 dp.
                int side = Math.min(picture.getWidth(), picture.getHeight());
                Bitmap square = Bitmap.createBitmap(picture, (picture.getWidth() - side) / 2,
                        (picture.getHeight() - side) / 2, side, side);
                int size = (int) (44 * density);
                art.setImageBitmap(side > size ? Bitmap.createScaledBitmap(square, size, size, true) : square);
            } else {
                try {
                    // The square icon: a wide banner cropped to a square cuts the app's name off.
                    art.setImageDrawable(pm.getApplicationIcon(pkg));
                } catch (Exception e) {
                    art.setImageDrawable(null);
                }
            }
        }
        artSource = picture;
        artPkg = pkg;
    }

    private static String text(MediaMetadata md, String key) {
        CharSequence t = md != null ? md.getText(key) : null;
        return t != null ? t.toString() : null;
    }

    private void show(MediaController session) {
        shown = session;
        place();
    }

    /**
     * Shows the card just above the dock when there is a session, room under the top bar, and the grid
     * sits at its top; otherwise hides it. The top shelf name is shortened so it never runs under it.
     */
    private void place() {
        boolean want = shown != null && !broken && !grid.canScrollVertically(-1) && grid.getHeight() > 0;
        float top = 0;
        if (want) {
            card.measure(View.MeasureSpec.makeMeasureSpec(root.getWidth(), View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(root.getHeight(), View.MeasureSpec.AT_MOST));
            Rect g = new Rect();
            grid.getDrawingRect(g);
            root.offsetDescendantRectToMyCoords(grid, g);
            Rect bar = new Rect();
            topBar.getDrawingRect(bar);
            root.offsetDescendantRectToMyCoords(topBar, bar);
            top = g.top + grid.getPaddingTop() - 22 * density - card.getMeasuredHeight();
            want = top >= bar.bottom + 14 * density;   // no room with rows under the dock: leave it out
        }
        if (want) {
            card.setY(top);
            // The name and the card sit at opposite ends of the row (mirrored in right-to-left).
            Rect hero = new Rect();
            heroName.getDrawingRect(hero);
            root.offsetDescendantRectToMyCoords(heroName, hero);
            int cardSpan = ((FrameLayout.LayoutParams) card.getLayoutParams()).getMarginEnd() + card.getMeasuredWidth();
            int room = root.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL
                    ? hero.right - cardSpan : root.getWidth() - cardSpan - hero.left;
            heroName.setMaxWidth(Math.max(0, room - (int) (24 * density)));
            if (!visible) {
                visible = true;
                card.setVisibility(View.VISIBLE);
            }
        } else if (visible) {
            visible = false;
            if (card.hasFocus()) home.focusDockEnd();
            card.setVisibility(View.GONE);
            heroName.setMaxWidth(Integer.MAX_VALUE);
        }
    }

    // ---- listeners ------------------------------------------------------------------------------------

    @Override
    public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
        // The card itself: a new title changed its width while it has focus, so refit the highlight.
        if (v == card) {
            if (card.hasFocus()) home.glassOnto(card, FOCUS_SCALE, 16 * density);
        } else {
            grid.post(this);   // the grid: after the layout settles, pick and place again
        }
    }

    @Override
    public void onScrollChanged() {
        if (shown != null) place();
    }

    /** Focus looks like a tile's: the card lifts and the glass highlight springs onto it. */
    @Override
    public void onFocusChange(View v, boolean focused) {
        float s = focused ? FOCUS_SCALE : 1f;
        if (animations) v.animate().scaleX(s).scaleY(s).setDuration(180).start();
        else {
            v.setScaleX(s);
            v.setScaleY(s);
        }
        if (focused) home.glassOnto(v, FOCUS_SCALE, 16 * density);
        else v.setFocusable(false);
    }

    /** OK: play or pause. */
    @Override
    public void onClick(View v) {
        try {
            MediaController c = shown;
            if (c == null) return;
            PlaybackState st = c.getPlaybackState();
            if (st != null && isActive(st.getState())) c.getTransportControls().pause();
            else c.getTransportControls().play();
        } catch (Exception | LinkageError e) {
            fail(e);
        }
    }

    /** Left/Right: previous/next, one per press. Down: back to the dock's last app. */
    @Override
    public boolean onKey(View v, int keyCode, KeyEvent event) {
        boolean left = keyCode == KeyEvent.KEYCODE_DPAD_LEFT;
        if (!left && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT && keyCode != KeyEvent.KEYCODE_DPAD_DOWN) return false;
        if (event.getAction() != KeyEvent.ACTION_DOWN || event.getRepeatCount() > 0) return true;
        if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
            home.focusDockEnd();
            return true;
        }
        try {
            MediaController c = shown;
            if (c != null && left) c.getTransportControls().skipToPrevious();
            else if (c != null) c.getTransportControls().skipToNext();
        } catch (Exception | LinkageError e) {
            fail(e);
        }
        return true;
    }

    /** Hold OK: the app, as its tile opens it. */
    @Override
    public boolean onLongClick(View v) {
        MediaController c = shown;
        if (c == null) return true;
        PackageManager pm = home.getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(c.getPackageName());
        if (i == null) i = pm.getLaunchIntentForPackage(c.getPackageName());
        if (i != null) home.open(i);
        return true;
    }
}
