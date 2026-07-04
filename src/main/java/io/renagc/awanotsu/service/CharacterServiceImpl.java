package io.renagc.awanotsu.service;

import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.character.CharacterService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 */
public final class CharacterServiceImpl extends io.renagc.awanotsu.proto.character.CharacterServiceGrpc.CharacterServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CharacterServiceImpl.class);

    private final ServerContext ctx;

    public CharacterServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void gainFriendshipRankReward(io.renagc.awanotsu.proto.character.GainFriendshipRankRewardRequest request, StreamObserver<io.renagc.awanotsu.proto.character.GainFriendshipRankRewardResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.character.GainFriendshipRankRewardResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
