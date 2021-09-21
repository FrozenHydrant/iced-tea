import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.MutableAudioFrame;
import com.sedmelluq.discord.lavaplayer.track.playback.NonAllocatingAudioFrameBuffer;
import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.message.MessageCreateEvent;
import discord4j.core.object.VoiceState;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.Member;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.object.entity.channel.VoiceChannel;
import discord4j.rest.util.Color;
import discord4j.voice.AudioProvider;
import discord4j.voice.VoiceConnection;
import io.github.cdimascio.dotenv.Dotenv;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;

public class Bot {
    private static final Map<String, Command> commands = new HashMap<>();
    public static final AudioPlayerManager PLAYER_MANAGER = new DefaultAudioPlayerManager();
    public static Map<Snowflake, GuildAudioManager> guildAudios = new HashMap<>();

    public static void main(final String[] args) {
        PLAYER_MANAGER.getConfiguration().setFrameBufferFactory(NonAllocatingAudioFrameBuffer::new);
        AudioSourceManagers.registerRemoteSources(PLAYER_MANAGER);
        System.out.println("gogogo!");

        //final AudioPlayer player = PLAYER_MANAGER.createPlayer();
        //AudioProvider provider = new LavaPlayerAudioProvider(player);
        //final TrackScheduler scheduler = new TrackScheduler(player);

        commands.put("ping", event -> {
            System.out.println("Doing stuff! ping!");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            channel.createMessage("**Pong!**").block();
        });
        commands.put("join", event -> {
            System.out.println("Joining channel!");
            joinCall(event);
        });
        commands.put("dc", event -> {
            //todo: add code
            System.out.println("disconnecting.");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            VoiceConnection currentVc = guildAudios.get(snowflake).vc;
            if (currentVc != null) {
                currentVc.disconnect().block();
                channel.createMessage("**Disconnected successfully. Queue will be preserved. Use %clear to clear it. **").block();
                guildAudios.get(snowflake).vc = null;
            }
        });
        commands.put("np", event -> {
            System.out.println("now playing called");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            final TrackScheduler currentTrackScheduler = guildAudios.get(snowflake).scheduler;
            AudioTrack currentTrack = currentTrackScheduler.getCurrentSong();
            if (currentTrack != null) {
                final long cposition = currentTrack.getPosition();
                final long clength = currentTrack.getDuration();
                final String cdesc = (cposition / 60000) + ":" + (cposition / 1000 % 60 < 10 ? "0" : "") + (cposition / 1000 % 60) + " / " + (clength / 60000) + ":" + (clength / 1000 % 60 < 10 ? "0" : "") + (clength / 1000 % 60);
                final String curi = currentTrack.getInfo().uri;
                //System.out.println(currentTrack.getInfo().title);
                //System.out.println(cdesc);
                System.out.println(curi);
                channel.createEmbed(spec ->
                        spec.setColor(Color.RUST).setTitle("Now Playing: " + currentTrack.getInfo().title).setUrl(curi).setDescription(cdesc).setThumbnail("https://i.ytimg.com/vi/" + curi.substring(curi.indexOf("=") + 1) + "/hq720.jpg").addField("Author", currentTrack.getInfo().author, false)
                ).block();
            } else {
                channel.createMessage("**No track is currently playing.**").block();
            }

        });
        commands.put("shuffle", event -> {
            System.out.println("shuffle");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final TrackScheduler currentTrackScheduler = guildAudios.get(snowflake).scheduler;
            currentTrackScheduler.shuffle();
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            channel.createMessage("**Shuffled playlist.**").block();
        });
        commands.put("skip", event -> {
            System.out.println("skip");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final TrackScheduler currentTrackScheduler = guildAudios.get(snowflake).scheduler;
            currentTrackScheduler.skipSong();
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            channel.createMessage("**Skipped song.**").block();
        });
        commands.put("clear", event -> {
            System.out.println("cleared");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final TrackScheduler currentTrackScheduler = guildAudios.get(snowflake).scheduler;
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            channel.createMessage("**Cleared queue.**").block();
            currentTrackScheduler.clearTracks();

        });
        commands.put("play", event -> {
            System.out.println("playing");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final TrackScheduler currentTrackScheduler = guildAudios.get(snowflake).scheduler;
            final Message message = event.getMessage();
            final String content = message.getContent();
            //System.out.println("playing " + content);
            if (content.contains(" ")) {
                //System.out.println(content.substring(content.indexOf(" ") + 1));
                PLAYER_MANAGER.loadItem(content.substring(content.indexOf(" ") + 1), currentTrackScheduler);
                joinCall(event);
            }
        });
        commands.put("queue", event -> {
            System.out.println("queue");
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final TrackScheduler currentTrackScheduler = guildAudios.get(snowflake).scheduler;
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            final AudioTrack currentTrack = currentTrackScheduler.getCurrentSong();
            final String messageToBeSent = currentTrackScheduler.songList();
            //channel.createMessage(messageToBeSent).block();
            if (messageToBeSent.length() > 0) {
                if (currentTrack != null) {
                    final String curi = currentTrack.getInfo().uri;
                    channel.createEmbed(spec ->
                            spec.setColor(Color.RUST).setTitle("Queue").addField("Current Song", currentTrack.getInfo().title, false).setUrl(curi).setThumbnail("https://i.ytimg.com/vi/" + curi.substring(curi.indexOf("=") + 1) + "/hq720.jpg").addField("Queue", messageToBeSent, false).addField("Queue Size", Integer.toString(currentTrackScheduler.queueSize()), false)
                    ).block();
                } else {
                    throw new IllegalStateException();
                }
            } else {
                channel.createMessage("**There are no songs in the queue.**").block();
            }


        });
        commands.put("help", event -> {
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            StringBuilder s = new StringBuilder();
            for (String i : commands.keySet()) {
                s.append(i);
                s.append(", ");
            }
            s.append(" are existing commands.");
            channel.createEmbed(spec -> spec.setColor(Color.RUST).setTitle("Help").addField("Available Commands", s.toString(), false)).block();
        });
        commands.put("removedupes", event -> {
            Guild currentGuild = event.getGuild().block();
            Snowflake snowflake = getCurrentSnowflake(currentGuild);
            final MessageChannel channel = guildAudios.get(snowflake).msc;
            TrackScheduler currentTrackScheduler = guildAudios.get(snowflake).scheduler;
            channel.createMessage("**Removed " + currentTrackScheduler.clearDupes() + " duplicate songs from the queue.**").block();
        });

        Dotenv dotenv = Dotenv.load();
        final String token = dotenv.get("TOKEN");
        final DiscordClient client = DiscordClient.create(token);
        final GatewayDiscordClient gateway = client.login().block();


        gateway.getEventDispatcher().on(MessageCreateEvent.class).subscribe(event -> {
            final String messageContent = event.getMessage().getContent();
            final Guild guildSentIn = event.getGuild().block();
            final MessageChannel currentChannel = event.getMessage().getChannel().block();
            Member messagingPerson = null;
            if (event.getMember().isPresent()) {
                messagingPerson = event.getMember().get();
            }
            if (currentChannel != null && messagingPerson != null && !messagingPerson.isBot() && messageContent.length() > 0 && guildSentIn != null) { //prevent processing of commands sent by other bots, or in private messages
                if (messageContent.charAt(0) == '%') {
                    final Snowflake snowflake = getCurrentSnowflake(guildSentIn);
                    if (!guildAudios.containsKey(snowflake)) {
                        guildAudios.put(snowflake, new GuildAudioManager(snowflake));
                    }
                    guildAudios.get(snowflake).msc = currentChannel;
                    int firstSpace = messageContent.indexOf(" ");
                    String commandInQuestion = messageContent.substring(1, firstSpace == -1 ? messageContent.length() : firstSpace);
                    if (commands.containsKey(commandInQuestion)) {
                        commands.get(commandInQuestion).execute(event);
                    }
                }
            }
        });


        gateway.onDisconnect().block();
    }

    public static Snowflake getCurrentSnowflake(Guild thisGuild) {
        if (thisGuild != null) {
            return thisGuild.getId();
        } else {
            throw new NullPointerException();
        }
    }

    public static boolean joinCall(MessageCreateEvent event) {
        final Member member = event.getMember().orElse(null);
        Guild currentGuild = event.getGuild().block();
        Snowflake snowflake = getCurrentSnowflake(currentGuild);
        boolean success = false;
        //final MessageChannel channel = guildAudios.get(snowflake).msc;
        //final MessageChannel messageChannel = event.getMessage().getChannel().block()
        if (member != null) {
            final AudioProvider currentProvider = guildAudios.get(snowflake).provider;
            //System.out.println(guildAudios);

            VoiceState currentVoiceState = member.getVoiceState().block();
            if (currentVoiceState != null) {
                VoiceChannel currentVoiceChannel = currentVoiceState.getChannel().block();
                if (currentVoiceChannel != null) {
                    guildAudios.get(snowflake).vc = currentVoiceChannel.join(spec -> spec.setProvider(currentProvider)).retry(5).block(Duration.ofSeconds(5));
                    success = true;
                    //channel.createMessage("**Joined successfully.**").block();

                }
            }
        }
        return success;
    }

    public static final class LavaPlayerAudioProvider extends AudioProvider {

        private final AudioPlayer player;
        private final MutableAudioFrame frame = new MutableAudioFrame();

        public LavaPlayerAudioProvider(final AudioPlayer player) {
            // Allocate a ByteBuffer for Discord4J's AudioProvider to hold audio data
            // for Discord
            super(
                    ByteBuffer.allocate(
                            StandardAudioDataFormats.DISCORD_OPUS.maximumChunkSize()
                    )
            );
            // Set LavaPlayer's MutableAudioFrame to use the same buffer as the one we
            // just allocated
            frame.setBuffer(getBuffer());
            this.player = player;
        }

        @Override
        public boolean provide() {
            // AudioPlayer writes audio data to its AudioFrame
            final boolean didProvide = player.provide(frame);
            // If audio was provided, flip from write-mode to read-mode
            if (didProvide) {
                getBuffer().flip();
            }
            return didProvide;
        }
    }

    public static class GuildAudioManager {
        public final AudioPlayer ap;
        public final AudioProvider provider;
        public final TrackScheduler scheduler;
        public VoiceConnection vc;
        public MessageChannel msc;

        private GuildAudioManager(Snowflake id) {
            ap = PLAYER_MANAGER.createPlayer();
            provider = new LavaPlayerAudioProvider(ap);
            scheduler = new TrackScheduler(ap, id);
            ap.addListener(scheduler);
        }
    }

    public final static class TrackScheduler extends AudioEventAdapter implements AudioLoadResultHandler {
        private final Snowflake id;
        private final AudioPlayer player;
        private final ArrayList<AudioTrack> currentTracks = new ArrayList<>();

        public TrackScheduler(final AudioPlayer player, final Snowflake id) {
            this.player = player;
            this.id = id;
        }

        public int queueSize() {
            return currentTracks.size();
        }

        public String songList() {
            StringBuilder songListifier = new StringBuilder();
            int count = 0;
            for (AudioTrack t : currentTracks) {
                count++;
                StringBuilder addition = new StringBuilder();
                addition.append(count);
                addition.append(". ");
                addition.append(t.getInfo().title);
                //addition.append(" @ ");
                //addition.append(t.getInfo().uri);
                addition.append("\n");
                if (songListifier.length() + addition.length() > 500) {
                    songListifier.append("... and many others.");
                    break;
                } else {
                    songListifier.append(addition);
                }

            }
            return songListifier.toString();
        }

        @Override
        public void trackLoaded(final AudioTrack track) {
            // LavaPlayer found an audio source for us to play
            //player.playTrack(track);
            boolean playing = player.startTrack(track, true);
            MessageChannel cmsc = guildAudios.get(id).msc;
            System.out.println("track loaded");
            if (!playing) {
                //player.playTrack(track);
                if (loadTrack(track)) {
                    cmsc.createMessage("**Added `" + track.getInfo().title + "` to the queue.**").block();
                } else {
                    cmsc.createMessage("**Could not add `" + track.getInfo().title + "` to the queue, because the queue is too large! A maximum of 2000 songs are allowed in the queue.**").block();
                }
            } else {
                cmsc.createMessage("**Now playing: `" + track.getInfo().title + "`.**").block();
            }


        }

        public void skipSong() {
            if (currentTracks.size() > 0) {
                player.startTrack(currentTracks.remove(0), false);
            } else {
                player.stopTrack();
            }

        }

        private boolean loadTrack(AudioTrack t) {
            if (currentTracks.size() < 2000) {
                currentTracks.add(t);
                return true;
            } else {
                return false;
            }
        }

        @Override
        public void playlistLoaded(final AudioPlaylist playlist) {
            // LavaPlayer found multiple AudioTracks from some playlist
            List<AudioTrack> temp = playlist.getTracks();
            MessageChannel cmsc = guildAudios.get(id).msc;
            boolean playing = player.startTrack(temp.get(0), true);
            int start = playing ? 1 : 0;
            for (int i = start; i < temp.size(); i++) {
                if (!loadTrack(temp.get(i))) {
                    cmsc.createMessage("**Only added " + i + " songs to the queue, because the queue is at its maximum capacity.**").block();
                    return;
                }
            }
            cmsc.createMessage("**Added " + temp.size() + " songs to the queue.**").block();


        }

        @Override
        public void noMatches() {

            // LavaPlayer did not find any audio to extract
        }

        public void shuffle() {
            Collections.shuffle(currentTracks);
        }

        @Override
        public void loadFailed(final FriendlyException exception) {
            // LavaPlayer could not parse an audio source for some reason
            System.out.println("load failed");
        }

        public void clearTracks() {
            currentTracks.clear();
        }

        public int clearDupes() {
            HashSet<String> temp = new HashSet<>();
            int c = 0;
            for (int i = 0; i < currentTracks.size(); i++) {
                if (!temp.add(currentTracks.get(i).getInfo().uri)) {
                    currentTracks.remove(i);
                    c++;
                    i--;
                }
                ;
            }
            return c;
        }

        public AudioTrack getCurrentSong() {
            //AudioTrack curTrack = player.getPlayingTrack();
            //String ans = "No track is currently playing!";
            //if (curTrack != null) {
            //    ans = curTrack.getInfo().title + "\n";
            //    ans += (curTrack.getPosition() / 60000) + ":" + (curTrack.getPosition() / 1000 % 60 < 10 ? "0" : "") + (curTrack.getPosition() / 1000 % 60) + " / " + (curTrack.getDuration() / 60000) + ":" + (curTrack.getDuration() / 1000 % 60 < 10 ? "0" : "") + (curTrack.getDuration() / 1000 % 60);
            //}
            return player.getPlayingTrack();
        }

        @Override
        public void onTrackEnd(final AudioPlayer player, final AudioTrack track, final AudioTrackEndReason endReason) {
            System.out.println("deciding what to do at the end of time.");
            if (endReason.mayStartNext) {
                System.out.println("Song ended normally. Starting next track (if in existence)");
                if (currentTracks.size() > 0) {
                    skipSong();
                }
            } else {
                System.out.println("Not starting new track. Reason: " + endReason.name());
            }

            // endReason == FINISHED: A track finished or died by an exception (mayStartNext = true).
            // endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
            // endReason == STOPPED: The player was stopped.
            // endReason == REPLACED: Another track started playing while this had not finished
            // endReason == CLEANUP: Player hasn't been queried for a while, if you want you can put a
            //                       clone of this back to your queue
        }

        @Override
        public void onTrackStart(AudioPlayer player, AudioTrack track) {
            System.out.println("New track started");
        }

        @Override
        public void onPlayerPause(AudioPlayer player) {
            System.out.println("player paused");
        }
    }

    public interface Command {
        void execute(MessageCreateEvent event);
    }
}
