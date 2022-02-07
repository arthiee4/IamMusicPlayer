package dev.felnull.imp.music.ringer;

import dev.architectury.networking.NetworkManager;
import dev.felnull.imp.music.MusicPlaybackInfo;
import dev.felnull.imp.networking.IMPPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import java.util.*;

public class MusicRing {
    private final Map<UUID, IMusicRinger> ringers = new HashMap<>();
    private final Map<UUID, RingedPlayerInfos> playerInfos = new HashMap<>();
    private final Set<UUID> waitRingers = new HashSet<>();
    private long baseTime;
    private long pauseTime;
    private long lastTime;

    public MusicRing() {
        this.baseTime = System.currentTimeMillis();
    }

    protected void tick(ServerLevel level) {
        for (IMusicRinger value : ringers.values()) {
            if (!value.isRingerExist(level)) {
                ringers.remove(value.getRingerUUID());
                playerInfos.remove(value.getRingerUUID());
                waitRingers.remove(value.getRingerUUID());
                break;
            }
            var ms = value.getRingerMusicSource(level);
            boolean stopFlg = false;
            if (ms == null) {
                if (value.isRingerPlaying(level))
                    value.setRingerPlaying(level, false);
                stopFlg = true;
            } else {
                if (value.isRingerPlaying(level)) {
                    var pr = playerInfos.get(value.getRingerUUID());
                    if (pr == null) {
                        pr = new RingedPlayerInfos(value.getRingerUUID(), level, getTime());
                        pr.sendFirstPackets(level);
                        playerInfos.put(value.getRingerUUID(), pr);
                        waitRingers.add(value.getRingerUUID());
                    }
                } else {
                    stopFlg = true;
                }
            }

            var pr = playerInfos.get(value.getRingerUUID());
            if (pr != null) {
                if (stopFlg) {
                    playerInfos.remove(value.getRingerUUID());
                    waitRingers.remove(value.getRingerUUID());
                } else {
                    if (pr.tick(level, getTime())) {
                        waitRingers.remove(value.getRingerUUID());
                        long eq = getTime() - lastTime;
                        if (value.getRingerMusicSource(level).getDuration() >= value.getRingerPosition(level) + eq) {
                            value.setRingerPosition(level, value.getRingerPosition(level) + eq);
                        } else {
                            value.setRingerPosition(level, 0);
                            if (value.isRingerLoop(level)) {
                                playerInfos.remove(value.getRingerUUID());
                            } else {
                                value.setRingerPlaying(level, false);
                            }
                        }
                    }
                }
            }

        }
        lastTime = getTime();
    }

    protected boolean isWaitRinger(UUID uuid) {
        return waitRingers.contains(uuid);
    }

    protected void addRinger(IMusicRinger ringer) {
        if (!ringers.containsKey(ringer.getRingerUUID()))
            ringers.put(ringer.getRingerUUID(), ringer);
    }

    protected Map<UUID, IMusicRinger> getRingers() {
        return ringers;
    }

    protected void pause() {
        this.pauseTime = System.currentTimeMillis();
    }

    protected void unPause() {
        this.baseTime += System.currentTimeMillis() - this.pauseTime;
        this.pauseTime = 0;
    }

    private long getTime() {
        return System.currentTimeMillis() - this.baseTime;
    }

    protected void depose() {
        ringers.clear();
        playerInfos.clear();
        waitRingers.clear();
    }

    private MusicPlaybackInfo getPlaybackInfo(IMusicRinger ringer, ServerLevel level) {
        var tr = ringer.getRingerTracker(level);
        return new MusicPlaybackInfo(tr.getKey(), tr.getValue(), ringer.getRingerVolume(level), ringer.getRingerRange(level));
    }

    protected void addReadyPlayer(ServerPlayer player, UUID uuid, UUID waitUUID, boolean result, boolean retry) {
        var pr = playerInfos.get(uuid);
        if (pr != null && pr.waitUUID.equals(waitUUID))
            pr.addReadyPlayer(player, result, result);
    }

    private class RingedPlayerInfos {
        private final UUID uuid;
        private final UUID waitUUID = UUID.randomUUID();
        private final List<UUID> firstWaitPlayers = new ArrayList<>();
        private final List<UUID> listenPlayers = new ArrayList<>();
        private final long startTime;
        private boolean notWait;

        public RingedPlayerInfos(UUID uuid, ServerLevel level, long startTime) {
            this.uuid = uuid;
            this.startTime = startTime;
            firstWaitPlayers.addAll(level.players().stream().filter(n -> canListen(n, level)).map(n -> n.getGameProfile().getId()).toList());
        }

        private void sendFirstPackets(ServerLevel level) {
            for (UUID firstWaitPlayer : firstWaitPlayers) {
                var player = level.getPlayerByUUID(firstWaitPlayer);
                if (player instanceof ServerPlayer serverPlayer)
                    NetworkManager.sendToPlayer(serverPlayer, IMPPackets.MUSIC_READY, new IMPPackets.MusicReadyMessage(waitUUID, uuid, getRinger().getRingerMusicSource(level), getPlaybackInfo(getRinger(), level), getRinger().getRingerPosition(level)).toFBB());
            }
        }

        private void sendStopPackets(UUID player, ServerLevel level) {
            var pl = level.getPlayerByUUID(player);
            if (pl != null) {

            }
            System.out.println("stop");
        }

        private void sendStartPacket(UUID player, ServerLevel level) {
            System.out.println("start");
        }

        private void addReadyPlayer(ServerPlayer player, boolean result, boolean retry) {
            var id = player.getGameProfile().getId();
            if (result) {
                listenPlayers.add(id);
            }
            firstWaitPlayers.remove(id);
        }

        private boolean tick(ServerLevel level, long currentTime) {
            if (notWait) {
                List<UUID> nl = new ArrayList<>();
                for (ServerPlayer player : level.players()) {
                    if (canListen(player, level)) {
                        var id = player.getGameProfile().getId();
                        nl.add(id);
                        if (!listenPlayers.contains(id))
                            sendStartPacket(id, level);
                    }
                }

                for (UUID listenPlayer : listenPlayers) {
                    if (!nl.contains(listenPlayer))
                        sendStopPackets(listenPlayer, level);
                }

                listenPlayers.clear();
                listenPlayers.addAll(nl);

                return true;
            }
            if (canPlayPlayersCheck(level, currentTime)) {
                notWait = true;
                return true;
            }
            return false;
        }

        private boolean canPlayPlayersCheck(ServerLevel level, long currentTime) {
            long eq = currentTime - startTime;
            if (eq > getMaxWaitTime())
                return true;

            List<UUID> removes = new ArrayList<>();
            for (UUID waiter : firstWaitPlayers) {
                var pl = level.getPlayerByUUID(waiter);
                if (pl == null || !canListen(pl, level))
                    removes.add(waiter);
            }
            firstWaitPlayers.removeAll(removes);
            return firstWaitPlayers.isEmpty();
        }

        private boolean canListen(Player player, ServerLevel level) {
            return player.level == level && Math.sqrt(player.distanceToSqr(getRinger().getRingerVec3Position(level))) <= getRinger().getRingerRange(level) + 30;
        }

        private IMusicRinger getRinger() {
            return ringers.get(uuid);
        }
    }

    public static long getMaxWaitTime() {
        return 1000 * 10;
    }
}
