package io.renagc.awanotsu.client;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import io.renagc.awanotsu.auth.AuthInterceptor;
import io.renagc.awanotsu.proto.common.PlayerData;
import io.renagc.awanotsu.proto.player.GetPlayerDataRequest;
import io.renagc.awanotsu.proto.player.GetPlayerDataResponse;
import io.renagc.awanotsu.proto.player.PlayerServiceGrpc;
import io.renagc.awanotsu.proto.player.RegisterRequest;
import io.renagc.awanotsu.proto.player.RegisterResponse;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/** Throwaway: register a fresh player and print the full GetPlayerData response (TextFormat + summaries). */
public final class DumpClient {
    public static void main(String[] args) throws Exception {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 20000;
        ManagedChannel channel = NettyChannelBuilder.forAddress(host, port).usePlaintext().build();
        try {
            RegisterResponse reg = PlayerServiceGrpc.newBlockingStub(channel)
                    .register(RegisterRequest.getDefaultInstance());
            Metadata md = new Metadata();
            md.put(AuthInterceptor.PLAYER_CREDENTIAL, reg.getCredential().getCredential());
            md.put(AuthInterceptor.PLAYER_ID, reg.getCredential().getId());
            var player = PlayerServiceGrpc.newBlockingStub(channel)
                    .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(md));
            GetPlayerDataResponse pdr = player.getPlayerData(GetPlayerDataRequest.getDefaultInstance());
            PlayerData pd = pdr.getPlayerData();

            System.out.println("### SUMMARY ###");
            System.out.println("memberCards=" + pd.getMemberCardsCount()
                    + " masterIds=" + pd.getMemberCardsList().stream()
                        .map(c -> c.getId() + ":m" + c.getMasterId()).collect(Collectors.joining(",")));
            System.out.println("supportCards=" + pd.getSupportCardsCount()
                    + " ids=" + pd.getSupportCardsList().stream()
                        .map(c -> c.getId() + ":m" + c.getMasterId()).collect(Collectors.joining(",")));
            System.out.println("decks=" + pd.getDecksCount());
            if (pd.getDecksCount() > 0) {
                System.out.println("deck0 cards=" + pd.getDecks(0).getCardsList().stream()
                        .map(c -> "slot" + c.getSlotIndex() + "(m=" + c.getMemberCardId()
                                + ",s=" + c.getSupportCardId() + ")").collect(Collectors.joining(" ")));
            }
            System.out.println("mainDeck=" + pd.getMainDeck());
            System.out.println("hasMyProfile=" + pd.hasMyProfile()
                    + " fav.cardId=" + pd.getMyProfile().getFavoriteMemberCard().getCardId()
                    + " favMasterId=" + pd.getMyProfile().getFavoriteMemberCardMasterId());
            System.out.println("hasGem=" + pd.hasGem() + " hasLiveBoost=" + pd.hasLiveBoost()
                    + " hasLiveSkip=" + pd.hasLiveSkip() + " hasPlayerMissionData=" + pd.hasPlayerMissionData()
                    + " hasLiveSetting=" + pd.hasLiveSetting() + " hasLiveStampReward=" + pd.hasLiveStampReward()
                    + " hasComeback=" + pd.hasComeback());
            System.out.println("stamps=" + pd.getStampsCount() + " items=" + pd.getItemsCount()
                    + " bandItems=" + pd.getBandItemsCount() + " liveMusic=" + pd.getLiveMusicCount()
                    + " laneSkins=" + pd.getLiveLaneSkinsCount() + " noteSkins=" + pd.getLiveNoteSkinsCount()
                    + " effectSkins=" + pd.getLiveNoteEffectSkinsCount() + " seGroups=" + pd.getLiveNoteSeGroupsCount());
            System.out.println("resp.hasNotification=" + pdr.hasNotification()
                    + " hasLimitation=" + pdr.hasLimitation() + " hasFriends=" + pdr.hasFriends()
                    + " hasInvitation=" + pdr.hasInvitation());

            System.out.println("\n### FULL TEXTFORMAT ###");
            System.out.println(pdr.toString());
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }
}
