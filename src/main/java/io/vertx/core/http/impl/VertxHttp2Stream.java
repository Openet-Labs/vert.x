/*
 * Copyright (c) 2011-2017 Contributors to the Eclipse Foundation
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0, or the Apache License, Version 2.0
 * which is available at https://www.apache.org/licenses/LICENSE-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
 */

package io.vertx.core.http.impl;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http2.EmptyHttp2Headers;
import io.netty.handler.codec.http2.Http2Headers;
import io.netty.handler.codec.http2.Http2Stream;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.StreamPriority;
import io.vertx.core.impl.ContextInternal;
import io.vertx.core.impl.VertxInternal;
import io.vertx.core.streams.impl.InboundBuffer;

/**
 * @author <a href="mailto:julien@julienviet.com">Julien Viet</a>
 */
abstract class VertxHttp2Stream<C extends Http2ConnectionBase> {

  private static final MultiMap EMPTY = new Http2HeadersAdaptor(EmptyHttp2Headers.INSTANCE);

  protected final C conn;
  protected final VertxInternal vertx;
  protected final ContextInternal context;
  protected final ChannelHandlerContext handlerContext;
  protected final Http2Stream stream;

  private InboundBuffer<Buffer> pending;
  private int pendingBytes;
  private MultiMap trailers;
  private boolean writable;
  private StreamPriority streamPriority = StreamPriority.DEFAULT;

  VertxHttp2Stream(C conn, Http2Stream stream, boolean writable) {
    this.conn = conn;
    this.vertx = conn.vertx();
    this.handlerContext = conn.handlerContext;
    this.stream = stream;
    this.context = conn.getContext();
    this.writable = writable;
    this.pending = new InboundBuffer<>(context, 5);

    pending.drainHandler(v -> {
      int numBytes = pendingBytes;
      pendingBytes = 0;
      conn.handler.consume(stream, numBytes);
    });

    pending.handler(this::handleData);
    pending.exceptionHandler(context.exceptionHandler());
    pending.emptyHandler(v -> {
      if (trailers != null) {
        handleEnd(trailers);
      }
    });

    pending.resume();
  }

  void onResetRead(long code) {
    handleReset(code);
  }

  boolean onDataRead(Buffer data) {
    boolean read = pending.write(data);
    if (!read) {
      pendingBytes += data.length();
    }
    return read;
  }

  void onWritabilityChanged() {
    synchronized (conn) {
      writable = !writable;
      handleInterestedOpsChanged();
    }
  }

  void onEnd() {
    onEnd(EMPTY);
  }

  void onEnd(MultiMap map) {
    synchronized (conn) {
      trailers = map;
      if (pending.isEmpty()) {
        handleEnd(trailers);
      }
    }
  }

  int id() {
    return stream.id();
  }

  public void doPause() {
    pending.pause();
  }

  public void doResume() {
    pending.resume();
  }

  public void doFetch(long amount) {
    pending.fetch(amount);
  }

  boolean isNotWritable() {
    synchronized (conn) {
      return !writable;
    }
  }

  void writeFrame(int type, int flags, ByteBuf payload) {
    conn.handler.writeFrame(stream, (byte) type, (short) flags, payload);
  }

  void writeHeaders(Http2Headers headers, boolean end) {
    conn.handler.writeHeaders(stream, headers, end, streamPriority.getStreamDependency(), streamPriority.getWeight(), streamPriority.isExclusive());
  }

  public void writePriorityFrame() {
    conn.handler.writePriority(stream, streamPriority.getStreamDependency(), streamPriority.getWeight(), streamPriority.isExclusive());
  }

  void writeData(ByteBuf chunk, boolean end) {
    conn.handler.writeData(stream, chunk, end);
  }

  void writeReset(long code) {
    conn.handler.writeReset(stream.id(), code);
  }

  void handleInterestedOpsChanged() {
  }

  void handleData(Buffer buf) {
  }

  void handleCustomFrame(int type, int flags, Buffer buff) {
  }

  void handleEnd(MultiMap trailers) {
  }

  void handleReset(long errorCode) {
  }

  void handleException(Throwable cause) {
  }

  void handleClose() {
  }
  
  public void priority(StreamPriority streamPriority) {
    this.streamPriority = streamPriority;
  }

  public StreamPriority priority() {
    return streamPriority;
  }
  
  void handlePriorityChange(StreamPriority streamPriority) {
  }
}
