package com.gathin.tvhome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Which session the Now playing card shows and what its two lines say. */
public class NowPlayingTest {
    private static final String SELF = "com.gathin.tvhome";

    @Test
    public void playingBeatsPausedWhateverTheOrder() {
        assertEquals(1, NowPlayingCard.choose(new int[]{NowPlayingCard.STATE_PAUSED, NowPlayingCard.STATE_PLAYING},
                new String[]{"a", "b"}, SELF));
        assertEquals(0, NowPlayingCard.choose(new int[]{6, NowPlayingCard.STATE_PLAYING},
                new String[]{"a", "b"}, SELF));
    }

    @Test
    public void firstPausedWhenNothingPlays() {
        assertEquals(1, NowPlayingCard.choose(new int[]{1, NowPlayingCard.STATE_PAUSED, NowPlayingCard.STATE_PAUSED},
                new String[]{"a", "b", "c"}, SELF));
    }

    @Test
    public void nothingToShow() {
        assertEquals(-1, NowPlayingCard.choose(new int[0], new String[0], SELF));
        assertEquals(-1, NowPlayingCard.choose(new int[]{0, 1, 7},
                new String[]{"a", "b", "c"}, SELF));
    }

    @Test
    public void skipsTheLauncherItself() {
        assertEquals(1, NowPlayingCard.choose(new int[]{NowPlayingCard.STATE_PLAYING, NowPlayingCard.STATE_PAUSED},
                new String[]{SELF, "b"}, SELF));
    }

    @Test
    public void activeStates() {
        assertTrue(NowPlayingCard.isActive(10));
        assertFalse(NowPlayingCard.isActive(NowPlayingCard.STATE_PAUSED));
        assertFalse(NowPlayingCard.isActive(7));
    }

    @Test
    public void titleFallsBack() {
        assertEquals("Song", NowPlayingCard.title(" Song ", "Display", "YouTube Music"));
        assertEquals("Display", NowPlayingCard.title("", "Display", "YouTube Music"));
        assertEquals("YouTube Music", NowPlayingCard.title(null, " ", "YouTube Music"));
    }

    @Test
    public void subtitleJoinsArtistAndApp() {
        assertEquals("Artist · YouTube Music", NowPlayingCard.subtitle("Artist", "Album", "YouTube Music", "Song"));
        assertEquals("Album · Spotify", NowPlayingCard.subtitle(null, "Album", "Spotify", "Song"));
        assertEquals("Spotify", NowPlayingCard.subtitle("", null, "Spotify", "Song"));
        assertEquals("Artist", NowPlayingCard.subtitle("Artist", null, "Spotify", "Spotify"));
        assertEquals("", NowPlayingCard.subtitle(null, null, "Spotify", "Spotify"));
    }

}
