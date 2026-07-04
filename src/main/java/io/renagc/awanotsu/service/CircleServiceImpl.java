package io.renagc.awanotsu.service;

import com.google.protobuf.Empty;
import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC impl for app.circle.CircleService.
 * NET-NEW binary-only service recovered from the IL2CPP dump.
 *
 * <p>The four auth-change RPCs all carry the same {@code ChangeAuthRequest{player_id}};
 * the target role is implied by which RPC is invoked (see circle.proto FLAG).
 */
public final class CircleServiceImpl extends io.renagc.awanotsu.proto.circle.CircleServiceGrpc.CircleServiceImplBase {

    private static final Logger log = LoggerFactory.getLogger(CircleServiceImpl.class);

    private final ServerContext ctx;

    public CircleServiceImpl(ServerContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void createCircle(io.renagc.awanotsu.proto.circle.SaveCircleRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.CreateCircleResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.CreateCircleResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getCircle(io.renagc.awanotsu.proto.circle.GetCircleSettingRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.GetCircleSettingResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.GetCircleSettingResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void editCircle(io.renagc.awanotsu.proto.circle.SaveCircleRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.EditCircleResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.EditCircleResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void search(io.renagc.awanotsu.proto.circle.SearchRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.SearchResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.SearchResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getRecommendedCircleList(io.renagc.awanotsu.proto.circle.GetRecommendedCircleListRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.GetRecommendedCircleListResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.GetRecommendedCircleListResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void getCircleDetail(io.renagc.awanotsu.proto.circle.GetCircleRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.GetCircleResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.GetCircleResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void transferMaster(io.renagc.awanotsu.proto.circle.ChangeAuthRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.ChangeAuthResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.ChangeAuthResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void transferSubmaster(io.renagc.awanotsu.proto.circle.ChangeAuthRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.ChangeAuthResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.ChangeAuthResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void setSubmaster(io.renagc.awanotsu.proto.circle.ChangeAuthRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.ChangeAuthResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.ChangeAuthResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void unsetSubmaster(io.renagc.awanotsu.proto.circle.ChangeAuthRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.ChangeAuthResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.ChangeAuthResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void removePlayer(io.renagc.awanotsu.proto.circle.RemovePlayerRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.RemovePlayerResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.RemovePlayerResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void exitCircle(io.renagc.awanotsu.proto.circle.ExitCircleRequest request, StreamObserver<io.renagc.awanotsu.proto.circle.ExitCircleResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.ExitCircleResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void deleteCircle(Empty request, StreamObserver<Empty> responseObserver) {
        responseObserver.onNext(Empty.getDefaultInstance());
        responseObserver.onCompleted();
    }

    @Override
    public void updateCircleTop(Empty request, StreamObserver<io.renagc.awanotsu.proto.circle.UpdateCircleTopResponse> responseObserver) {
        responseObserver.onNext(io.renagc.awanotsu.proto.circle.UpdateCircleTopResponse.getDefaultInstance());
        responseObserver.onCompleted();
    }
}
