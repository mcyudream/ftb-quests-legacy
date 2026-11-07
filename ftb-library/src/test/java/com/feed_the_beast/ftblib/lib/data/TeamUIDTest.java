package com.feed_the_beast.ftblib.lib.data;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TeamUIDTest
{
    @Test void savedSingleplayerTeamRetainsUID2()
    {
        Universe universe = new Universe(null);
        ForgeTeam team = universe.addLoadedTeam(2, "singleplayer", TeamType.SERVER);
        assertEquals(2, team.getUID());
        assertSame(team, universe.getTeam(2));
        assertSame(team, universe.getTeam("singleplayer"));
        assertFalse(team.needsSaving, "Loading an existing UID must not renumber or rewrite it");
    }

    @Test void duplicateUID2StillRefusesToOverwriteTheLoadedTeam()
    {
        Universe universe = new Universe(null);
        ForgeTeam original = universe.addLoadedTeam(2, "singleplayer", TeamType.SERVER);
        assertThrows(IllegalStateException.class, () -> universe.addLoadedTeam(2, "duplicate", TeamType.SERVER));
        assertSame(original, universe.getTeam(2));
        assertEquals(1, universe.getTeams().size());
    }

    @Test void fakePlayerUIDRemainsReserved()
    {
        Universe universe = new Universe(null);
        assertThrows(IllegalStateException.class, () -> universe.addLoadedTeam(1, "invalid", TeamType.PLAYER));
        assertTrue(universe.getTeams().isEmpty());
    }

    @Test void fullIntUIDsAndDuplicateProtectionRemainSupported()
    {
        Universe universe = new Universe(null);
        for (int uid : new int[]{-1234567, Integer.MIN_VALUE, Integer.MAX_VALUE, 0x12345678})
        {
            ForgeTeam team = universe.addLoadedTeam(uid, "team_" + Integer.toUnsignedString(uid), TeamType.SERVER);
            assertSame(team, universe.getTeam(uid));
            assertEquals(uid, team.getUID());
            assertThrows(IllegalStateException.class, () -> universe.addLoadedTeam(uid, "collision", TeamType.SERVER));
            assertSame(team, universe.getTeam(uid));
        }
    }

    @Test void missingUIDGetsANewUnoccupiedUIDWhileNewAllocationsStillReserve2()
    {
        Universe universe = new Universe(null);
        ForgeTeam team = universe.addLoadedTeam(0, "legacy", TeamType.SERVER);
        assertNotEquals(0, team.getUID());
        assertNotEquals(1, team.getUID());
        assertNotEquals(2, team.getUID());
        assertSame(team, universe.getTeam(team.getUID()));
        assertTrue(team.needsSaving);
        for (int candidate : new int[]{0, 1, 2, team.getUID()})
        {
            int allocated = universe.generateTeamUID(candidate);
            assertNotEquals(0, allocated);
            assertNotEquals(1, allocated);
            assertNotEquals(2, allocated);
            assertNotEquals(team.getUID(), allocated);
        }
    }
}
