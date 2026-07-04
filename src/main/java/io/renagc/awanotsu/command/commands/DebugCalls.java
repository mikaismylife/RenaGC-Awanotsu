package io.renagc.awanotsu.command.commands;

import io.grpc.stub.StreamObserver;
import io.renagc.awanotsu.ServerContext;
import io.renagc.awanotsu.proto.debug.GainGemRequest;
import io.renagc.awanotsu.proto.debug.GainGemResponse;
import io.renagc.awanotsu.proto.debug.GainItemRequest;
import io.renagc.awanotsu.proto.debug.GainItemResponse;
import io.renagc.awanotsu.proto.debug.GainMemberCardRequest;
import io.renagc.awanotsu.proto.debug.GainMemberCardResponse;
import io.renagc.awanotsu.proto.debug.GainMusicRequest;
import io.renagc.awanotsu.proto.debug.GainMusicResponse;
import io.renagc.awanotsu.proto.debug.GainSupportCardRequest;
import io.renagc.awanotsu.proto.debug.GainSupportCardResponse;
import io.renagc.awanotsu.service.DebugServiceImpl;

import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Invokes {@link DebugServiceImpl} (the recovered app.debug GM surface)
 * IN-PROCESS and synchronously. The debug Request messages have no recovered
 * fields yet, so callers pass the default request instance; once field numbers
 * are known these calls can carry real parameters (item id, amount, ...).
 */
final class DebugCalls {

    private final DebugServiceImpl debug;

    DebugCalls(ServerContext ctx) {
        this.debug = new DebugServiceImpl(ctx);
    }

    GainItemResponse gainItem(GainItemRequest req) {
        return capture(o -> debug.gainItem(req, o));
    }

    GainGemResponse gainGem(GainGemRequest req) {
        return capture(o -> debug.gainGem(req, o));
    }

    GainMusicResponse gainMusic(GainMusicRequest req) {
        return capture(o -> debug.gainMusic(req, o));
    }

    GainMemberCardResponse gainMemberCard(GainMemberCardRequest req) {
        return capture(o -> debug.gainMemberCard(req, o));
    }

    GainSupportCardResponse gainSupportCard(GainSupportCardRequest req) {
        return capture(o -> debug.gainSupportCard(req, o));
    }

    /** Run a unary call against an in-process observer and return its onNext. */
    private static <R> R capture(Consumer<StreamObserver<R>> invoker) {
        AtomicReference<R> result = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();
        invoker.accept(new StreamObserver<R>() {
            @Override
            public void onNext(R value) {
                result.set(value);
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
            }

            @Override
            public void onCompleted() {
            }
        });
        Throwable t = error.get();
        if (t != null) {
            throw new RuntimeException(t);
        }
        return result.get();
    }
}
