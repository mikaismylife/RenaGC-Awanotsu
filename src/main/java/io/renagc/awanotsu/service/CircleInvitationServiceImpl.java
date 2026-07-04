package io.renagc.awanotsu.service;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.circleinvitation.CircleInvitationService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 */
public final class CircleInvitationServiceImpl extends io.renagc.awanotsu.proto.circleinvitation.CircleInvitationServiceGrpc.CircleInvitationServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CircleInvitationServiceImpl.class);

    private final ServerContext ctx;

    public CircleInvitationServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void inviteCircle(io.renagc.awanotsu.proto.circleinvitation.InviteCircleRequest request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void revokeCircleInvitation(io.renagc.awanotsu.proto.circleinvitation.RevokeCircleInvitationRequest request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getInvitationList(io.renagc.awanotsu.proto.circleinvitation.GetInvitationListRequest request, StreamObserver<io.renagc.awanotsu.proto.circleinvitation.GetInvitationListResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circleinvitation.GetInvitationListResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getCircleInvitationRecommendList(Empty request, StreamObserver<io.renagc.awanotsu.proto.circleinvitation.GetCircleInvitationRecommendListResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circleinvitation.GetCircleInvitationRecommendListResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getInvitablePlayer(io.renagc.awanotsu.proto.circleinvitation.GetInvitablePlayerRequest request, StreamObserver<io.renagc.awanotsu.proto.circleinvitation.GetInvitablePlayerResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circleinvitation.GetInvitablePlayerResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getInvitingPlayer(Empty request, StreamObserver<io.renagc.awanotsu.proto.circleinvitation.GetInvitingPlayerResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circleinvitation.GetInvitingPlayerResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
