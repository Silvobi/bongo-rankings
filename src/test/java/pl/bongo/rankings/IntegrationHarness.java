package pl.bongo.rankings;

import io.netty.buffer.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.network.*;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.handshake.*;
import net.minecraft.network.protocol.login.*;
import net.minecraft.network.protocol.configuration.*;
import net.minecraft.network.protocol.common.*;
import net.minecraft.network.protocol.game.*;
import net.minecraft.network.chat.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.*;
import net.minecraft.nbt.*;
import net.minecraft.server.dialog.*;
import net.minecraft.server.dialog.action.CustomAll;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.inventory.*;
import net.minecraft.util.Crypt;
import com.mojang.serialization.Lifecycle;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;
import java.util.zip.*;
import javax.crypto.*;

public final class IntegrationHarness {
    static int port;
    static void check(boolean b,String label) { if(!b)throw new AssertionError(label);System.out.println("PASS: "+label); }
    static MultiActionDialog dialog(Bot bot) throws Exception {
        return (MultiActionDialog)((ClientboundShowDialogPacket)bot.await(p->p instanceof ClientboundShowDialogPacket,5000)).dialog().value();
    }
    static void click(Bot bot,ActionButton button) throws Exception {
        if (button.action().isEmpty()) return; // Vanilla performs the dialog's local after-action.
        CustomAll a=(CustomAll)button.action().orElseThrow();bot.send(new ServerboundCustomClickActionPacket(a.id(),Optional.empty()));
    }
    static String text(MultiActionDialog d) {
        return String.join("\n",d.common().body().stream().map(b -> b instanceof net.minecraft.server.dialog.body.PlainMessage p ? p.contents().getString() : "").toList());
    }
    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); port=Integer.parseInt(args[0]);
        BuiltInRegistries.ITEM.listElements().forEach(h->{if(!h.areComponentsBound())h.bindComponents(net.minecraft.core.component.DataComponentMap.EMPTY);});
        if (args.length > 1 && args[1].equals("close")) {
            for (String locale : List.of("pl_pl", "en_us")) try(Bot p=new Bot(locale.equals("pl_pl") ? "RankTester" : "RankEnglish",locale)) {
                for(int i=0;i<11;i++) {
                    p.command("toplist");var menu=dialog(p);
                    check(menu.common().afterAction()==DialogAction.CLOSE && menu.exitAction().orElseThrow().action().isEmpty(),
                            locale+": menu closes locally without a server response");
                    click(p,menu.actions().get(i));var ranked=dialog(p);
                    check(ranked.common().afterAction()==DialogAction.CLOSE && ranked.exitAction().orElseThrow().action().isEmpty(),
                            locale+": statistic "+i+" closes locally without a server response");
                    click(p,ranked.actions().getFirst());var back=dialog(p);
                    check(back.actions().size()==11,"back navigation still opens the statistic menu");
                    click(p,back.exitAction().orElseThrow()); // No packet, as on a vanilla client.
                }
                p.command("toplist");dialog(p);check(true,"toplist can be reopened after local close");
            }
            return;
        }
        if (args.length > 1 && args[1].equals("restart")) {
            try(Bot p=new Bot("RankTester","pl_pl")) {
                p.command("toplist");var menu=dialog(p);click(p,menu.actions().get(6));var gained=dialog(p);
                check(text(gained).contains("RankTester") && text(gained).contains("1,5 tys."),"XP gained survives a real server restart");
                click(p,gained.actions().getFirst());menu=dialog(p);click(p,menu.actions().get(7));var spent=dialog(p);
                check(text(spent).contains("219"),"XP spent survives a real server restart");
                click(p,spent.actions().getFirst());menu=dialog(p);click(p,menu.actions().get(2));var placed=dialog(p);
                check(text(placed).contains("RankTester"),"block-placement history survives a real server restart");
            }
            return;
        }
        try(Bot pl=new Bot("RankTester","pl_pl");Bot en=new Bot("RankEnglish","en_us");Bot fallback=new Bot("RankFallback","de_de")) {
            pl.command("toplist");var menu=dialog(pl);
            check(menu.actions().size()==11,"PL menu contains all 11 statistics");
            check(menu.actions().getFirst().button().label().getString().equals("Największy czas w grze"),"Polish client gets Polish labels");
            click(pl,menu.actions().getFirst());var time=dialog(pl);
            check(text(time).contains("138 godz. 15 min."),"offline time displays 138 hours without days/seconds");
            check(time.common().body().size()==11,"exactly TOP 10 rows plus age footer");
            pl.command("clan create RankingClan RGB");pl.drain(100);
            pl.command("clan change tag &#12ab34RGB");pl.drain(200);
            pl.command("rankingprobe");pl.message("PROBE_OK");
            pl.drain(12000);
            pl.command("toplist");menu=dialog(pl);click(pl,menu.actions().get(6));var xp=dialog(pl);
            check(text(xp).contains("RankTester") && text(xp).contains("1,5 tys."),"XP gains are cumulative and not double counted through level changes");
            var components=xp.common().body().getFirst();
            check(components instanceof net.minecraft.server.dialog.body.PlainMessage,"rank rows are native rich-text dialog components");
            Component row=((net.minecraft.server.dialog.body.PlainMessage)components).contents();
            final boolean[] prefix={false},gradient={false};
            row.visit((style,s)->{if(s.contains("RGB") && style.getColor()!=null && style.getColor().getValue()==0x12AB34)prefix[0]=true;
                if(s.equals("R") && style.getColor()!=null && style.getColor().getValue()==0xFFD3A9)gradient[0]=true;return Optional.empty();},Style.EMPTY);
            check(prefix[0] && gradient[0],"clan tag retains #12AB34 while nickname gets gold gradient");
            click(pl,xp.actions().getFirst());menu=dialog(pl);click(pl,menu.actions().get(7));var spent=dialog(pl);
            check(text(spent).contains("219"),"enchant + anvil costs are converted to exact XP points");
            click(pl,spent.actions().getFirst());menu=dialog(pl);click(pl,menu.actions().get(2));var placed=dialog(pl);
            check(text(placed).contains("RankTester") && text(placed).contains("1"),"successful block placement is counted");
            en.command("toplist");var english=dialog(en);
            fallback.command("toplist");var german=dialog(fallback);
            check(english.actions().getFirst().button().label().getString().equals("Most time played"),"English labels");
            check(german.actions().getFirst().button().label().equals(english.actions().getFirst().button().label()),"unsupported client language falls back to English");
            for(int i=0;i<11;i++) { en.command("toplist");var list=dialog(en);click(en,list.actions().get(i));var ranked=dialog(en);
                check(ranked.common().title().getString().startsWith("TOP 10"),"opens statistic "+i); }
            click(pl,placed.exitAction().orElseThrow());
            pl.command("toplist");dialog(pl);check(true,"close/back routes keep command usable");
        }
    }
    @SuppressWarnings({"rawtypes","unchecked"})
    static final class Bot implements AutoCloseable {
        final String name; UUID uuid;
        final Socket socket;
        InputStream input; OutputStream output;
        StreamCodec inbound=LoginProtocols.CLIENTBOUND.codec(),outbound=HandshakeProtocols.SERVERBOUND.codec();
        int compression=-1;
        RegistryAccess access=RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        final List<Component> messages=new ArrayList<>();
        final Map<UUID,Component> tabs=new HashMap<>();
        final Map<String,ClientboundSetPlayerTeamPacket.Parameters> teams=new HashMap<>();
        final List<Object> events=new ArrayList<>();
        final List<ClientboundResourcePackPushPacket> configurationPacks=new ArrayList<>();
        final Map<Integer,net.minecraft.network.protocol.PacketType<?>> types=new HashMap<>();
        @FunctionalInterface interface PackReply {
            void handle(Bot bot,ClientboundResourcePackPushPacket pack) throws Exception;
        }
        Bot(String name,String language) throws Exception {
            this(name,language,(bot,pack)-> {
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.ACCEPTED));
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.DOWNLOADED));
                bot.send(new ServerboundResourcePackPacket(pack.id(),ServerboundResourcePackPacket.Action.SUCCESSFULLY_LOADED));
            });
        }
        Bot(String name,String language,PackReply packReply) throws Exception {
            this.name=name; uuid=UUID.nameUUIDFromBytes(("OfflinePlayer:"+name).getBytes(StandardCharsets.UTF_8));
            socket=new Socket("127.0.0.1",port); socket.setSoTimeout(20000); input=socket.getInputStream();output=socket.getOutputStream();
            send(new ClientIntentionPacket(SharedConstants.getProtocolVersion(),"localhost",port,ClientIntent.LOGIN));
            outbound=LoginProtocols.SERVERBOUND.codec();send(new ServerboundHelloPacket(name,uuid));
            while(true) {
                Packet<?> packet=read();
                if(packet instanceof ClientboundHelloPacket hello) {
                    var secret=Crypt.generateSecretKey();send(new ServerboundKeyPacket(secret,hello.getPublicKey(),hello.getChallenge()));
                    input=new CipherInputStream(input,Crypt.getCipher(2,secret));output=new CipherOutputStream(output,Crypt.getCipher(1,secret));
                } else if(packet instanceof ClientboundLoginCompressionPacket threshold) compression=threshold.getCompressionThreshold();
                else if(packet instanceof ClientboundLoginDisconnectPacket kick) throw new AssertionError(kick.reason());
                else if(packet instanceof ClientboundLoginFinishedPacket finished) { uuid=finished.gameProfile().id(); break; }
            }
            send(ServerboundLoginAcknowledgedPacket.INSTANCE);inbound=ConfigurationProtocols.CLIENTBOUND.codec();outbound=ConfigurationProtocols.SERVERBOUND.codec();
            var options=new ClientInformation(language,2,ChatVisiblity.FULL,true,127,HumanoidArm.RIGHT,false,true,ParticleStatus.ALL);
            send(new ServerboundClientInformationPacket(options));
            while(true) {
                Packet<?> packet=read();
                if(packet instanceof ClientboundSelectKnownPacks) send(new ServerboundSelectKnownPacks(List.of()));
                else if(packet instanceof ClientboundResourcePackPushPacket pack) {
                    configurationPacks.add(pack);
                    packReply.handle(this,pack);
                }
                else if(packet instanceof ClientboundShowDialogPacket shown) {
                    MultiActionDialog dialog=(MultiActionDialog)shown.dialog().value();
                    CompoundTag fields=new CompoundTag();fields.putString("password","clans-test-password");fields.putString("repeat","clans-test-password");
                    CustomAll action=(CustomAll)dialog.actions().getFirst().action().orElseThrow();
                    send(new ServerboundCustomClickActionPacket(action.id(),Optional.of(fields)));
                } else if(packet instanceof ClientboundRegistryDataPacket data && data.registry().equals(Registries.CHAT_TYPE)) {
                    var chatTypes=new MappedRegistry<ChatType>(Registries.CHAT_TYPE,Lifecycle.stable());
                    for(var entry:data.entries()) chatTypes.register(ResourceKey.create(Registries.CHAT_TYPE,entry.id()),ChatType.DIRECT_CODEC.parse(NbtOps.INSTANCE,entry.data().orElseThrow()).getOrThrow(),RegistrationInfo.BUILT_IN);
                    chatTypes.freeze();
                    List<Registry<?>> registries=new ArrayList<>(access.registries().map(RegistryAccess.RegistryEntry::value).toList());registries.add(chatTypes);
                    access=new RegistryAccess.ImmutableRegistryAccess(registries).freeze();
                } else if(packet instanceof ClientboundKeepAlivePacket alive) send(new ServerboundKeepAlivePacket(alive.getId()));
                else if(packet instanceof ClientboundPingPacket ping) send(new ServerboundPongPacket(ping.getId()));
                else if(packet instanceof ClientboundDisconnectPacket kick) throw new AssertionError(kick.reason());
                else if(packet instanceof ClientboundFinishConfigurationPacket) break;
            }
            send(ServerboundFinishConfigurationPacket.INSTANCE);
            outbound=GameProtocols.SERVERBOUND_TEMPLATE.bind(RegistryFriendlyByteBuf.decorator(access),new GameProtocols.Context() {
                public boolean hasInfiniteMaterials(){return false;}public boolean canUseCommandBlocks(){return false;}
            }).codec();
            GameProtocols.CLIENTBOUND_TEMPLATE.details().listPackets((type,index)->types.put(index,type));
            send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
            send(new ServerboundPlayerLoadedPacket());
            drain(150);
        }
        String teamPrefix() { return teams.values().stream().filter(p->p.playerPrefix().getString().startsWith("[★")).findFirst().orElseThrow().playerPrefix().getString(); }
        void command(String command) throws Exception { send(new ServerboundChatCommandPacket(command)); }
        void chat(String message) throws Exception { send(new ServerboundChatPacket(message,Instant.now(),0,Optional.empty(),new LastSeenMessages.Update(0,new BitSet(),LastSeenMessages.Update.IGNORE_CHECKSUM))); }
        Component message(String contains) throws Exception {
            Object p=await(e->e instanceof Component c && c.getString().contains(contains),5000);return (Component)p;
        }
        Object await(Predicate<Object> predicate,long timeout) throws Exception {
            long deadline=System.currentTimeMillis()+timeout;
            while(System.currentTimeMillis()<deadline) {
                for(int i=0;i<events.size();i++) if(predicate.test(events.get(i))) return events.remove(i);
                poll(100);
            }
            throw new AssertionError(name+" timed out; recent chat="+messages.stream().map(Component::getString).toList());
        }
        void drain(long millis) throws Exception { long until=System.currentTimeMillis()+millis;do{poll(5);}while(System.currentTimeMillis()<until); }
        Object poll(long timeout) throws Exception {
            long deadline=System.currentTimeMillis()+timeout;
            while(input.available()==0 && socket.getInputStream().available()==0) {if(System.currentTimeMillis()>=deadline)return null;Thread.sleep(2);}
            var buffer=new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(frame()),access);
            Object result=null;
            try {
                var type=types.get(buffer.readVarInt());
                if(type==CommonPacketTypes.CLIENTBOUND_KEEP_ALIVE) send(new ServerboundKeepAlivePacket(ClientboundKeepAlivePacket.STREAM_CODEC.decode(buffer).getId()));
                else if(type==CommonPacketTypes.CLIENTBOUND_PING) send(new ServerboundPongPacket(ClientboundPingPacket.STREAM_CODEC.decode(buffer).getId()));
                else if(type==CommonPacketTypes.CLIENTBOUND_DISCONNECT) throw new AssertionError(ClientboundDisconnectPacket.STREAM_CODEC.decode(buffer).reason());
                else if(type==CommonPacketTypes.CLIENTBOUND_SHOW_DIALOG) result=ClientboundShowDialogPacket.STREAM_CODEC.decode(buffer);
                else if(type==CommonPacketTypes.CLIENTBOUND_RESOURCE_PACK_PUSH) result=ClientboundResourcePackPushPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_ADD_ENTITY) result=ClientboundAddEntityPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_REMOVE_ENTITIES) result=ClientboundRemoveEntitiesPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_SOUND) result=ClientboundSoundPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_STOP_SOUND) result=ClientboundStopSoundPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_POSITION) {
                    var p=ClientboundPlayerPositionPacket.STREAM_CODEC.decode(buffer);var c=p.change();send(new ServerboundAcceptTeleportationPacket(p.id(),c.position().x,c.position().y,c.position().z,c.yRot(),c.xRot()));
                } else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_INFO_UPDATE) {
                    var p=ClientboundPlayerInfoUpdatePacket.STREAM_CODEC.decode(buffer);
                    if(p.actions().contains(ClientboundPlayerInfoUpdatePacket.Action.UPDATE_DISPLAY_NAME)) for(var e:p.entries()) tabs.put(e.profileId(),e.displayName());
                    result=p;
                } else if(type==GamePacketTypes.CLIENTBOUND_SET_PLAYER_TEAM) {
                    var p=ClientboundSetPlayerTeamPacket.STREAM_CODEC.decode(buffer);p.getParameters().ifPresent(params->teams.put(p.getName(),params));
                    if(p.getTeamAction()==ClientboundSetPlayerTeamPacket.Action.REMOVE) teams.remove(p.getName());result=p;
                } else if(type==GamePacketTypes.CLIENTBOUND_SYSTEM_CHAT) result=ClientboundSystemChatPacket.STREAM_CODEC.decode(buffer).content();
                else if(type==GamePacketTypes.CLIENTBOUND_PLAYER_CHAT) {
                    var p=ClientboundPlayerChatPacket.STREAM_CODEC.decode(buffer);result=p.chatType().decorate(p.unsignedContent().orElseGet(()->Component.literal(p.body().content())));
                } else if(type==GamePacketTypes.CLIENTBOUND_DISGUISED_CHAT) {
                    var p=ClientboundDisguisedChatPacket.STREAM_CODEC.decode(buffer);result=p.chatType().decorate(p.message());
                } else if(type==GamePacketTypes.CLIENTBOUND_OPEN_SCREEN) result=ClientboundOpenScreenPacket.STREAM_CODEC.decode(buffer);
                else if(type==GamePacketTypes.CLIENTBOUND_CONTAINER_SET_CONTENT) result=ClientboundContainerSetContentPacket.STREAM_CODEC.decode(buffer);
            } finally {buffer.release();}
            if(result!=null){events.add(result);if(result instanceof Component c)messages.add(c);}
            return result;
        }
        Packet<?> read() throws Exception {ByteBuf b=Unpooled.wrappedBuffer(frame());try{return(Packet<?>)inbound.decode(b);}finally{b.release();}}
        byte[] frame() throws Exception {
            int size=varInt(input);if(size<0||size>8388608)throw new IOException("Bad frame size");byte[] bytes=new byte[size];
            for(int offset=0;offset<size;){int count=input.read(bytes,offset,size-offset);if(count<0)throw new EOFException();offset+=count;}
            if(compression>=0){var compressed=new ByteArrayInputStream(bytes);int original=varInt(compressed);bytes=original==0?compressed.readAllBytes():new InflaterInputStream(compressed).readAllBytes();}return bytes;
        }
        void send(Packet<?> packet) throws Exception {
            ByteBuf buffer=Unpooled.buffer();byte[] bytes;
            try{outbound.encode(buffer,packet);bytes=new byte[buffer.readableBytes()];buffer.readBytes(bytes);}finally{buffer.release();}
            if(compression>=0){var frame=new ByteArrayOutputStream();if(bytes.length>=compression){varInt(frame,bytes.length);var deflate=new DeflaterOutputStream(frame);deflate.write(bytes);deflate.finish();}else{varInt(frame,0);frame.write(bytes);}bytes=frame.toByteArray();}
            varInt(output,bytes.length);output.write(bytes);output.flush();
        }
        public void close() throws IOException {socket.close();}
    }
    static int varInt(InputStream input)throws IOException {int value=0;for(int i=0;i<5;i++){int b=input.read();if(b<0)throw new EOFException();value|=(b&127)<<(i*7);if((b&128)==0)return value;}throw new IOException("Bad VarInt");}
    static void varInt(OutputStream output,int value)throws IOException {do{int b=value&127;value>>>=7;output.write(b|(value==0?0:128));}while(value!=0);}
}
