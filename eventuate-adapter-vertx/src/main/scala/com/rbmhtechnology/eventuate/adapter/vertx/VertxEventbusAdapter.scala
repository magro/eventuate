/*
 * Copyright 2015 - 2016 Red Bull Media House GmbH <http://www.redbullmediahouse.com> - all rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rbmhtechnology.eventuate.adapter.vertx

import akka.actor.{ ActorRef, ActorSystem, Props }
import com.rbmhtechnology.eventuate.{ DurableEvent, ReplicationEndpoint }
import com.rbmhtechnology.eventuate.adapter.vertx.japi.{ StorageProvider => JStorageProvider }
import com.rbmhtechnology.eventuate.adapter.vertx.japi.rx.{ StorageProvider => RxStorageProvider }
import io.vertx.core.Vertx
import io.vertx.rxjava.core.{ Vertx => RxVertx }

import scala.collection.immutable.Seq

object VertxEventbusAdapter {

  import VertxConverters._

  def apply(adapterConfig: AdapterConfig, replicationEndpoint: ReplicationEndpoint, vertx: Vertx, storageProvider: StorageProvider)(implicit system: ActorSystem): VertxEventbusAdapter =
    new VertxEventbusAdapter(adapterConfig, replicationEndpoint, vertx, storageProvider)

  def create(adapterConfig: AdapterConfig,
    replicationEndpoint: ReplicationEndpoint,
    vertx: Vertx,
    storageProvider: JStorageProvider,
    system: ActorSystem): VertxEventbusAdapter =
    new VertxEventbusAdapter(adapterConfig, replicationEndpoint, vertx, storageProvider.asScala)(system)

  def create(adapterConfig: AdapterConfig,
    replicationEndpoint: ReplicationEndpoint,
    vertx: RxVertx,
    storageProvider: RxStorageProvider,
    system: ActorSystem): VertxEventbusAdapter =
    new VertxEventbusAdapter(adapterConfig, replicationEndpoint, vertx, storageProvider.asScala)(system)
}

class VertxEventbusAdapter(adapterConfig: AdapterConfig, replicationEndpoint: ReplicationEndpoint, vertx: Vertx, storageProvider: StorageProvider)(implicit system: ActorSystem) {

  import VertxEventbusInfo._

  private def registerCodec(): Unit =
    vertx.eventBus().registerDefaultCodec(classOf[DurableEvent], DurableEventMessageCodec(system))

  def activate(): Unit = {
    registerCodec()
    val supervisor = system.actorOf(LogAdapterSupervisor.props(logAdapters(adapterConfig.logDescriptors)))

    replicationEndpoint.activate()
  }

  private def logAdapters(logs: Seq[LogAdapterDescriptor]): Seq[Props] = {
    def log(name: String): Option[ActorRef] = replicationEndpoint.logs.get(name)

    logs.map {
      case l @ PublishReadLogAdapterDescriptor(n) =>
        log(n).map(PublishReadLogAdapter.props(logId(n, l.logType), _, eventbusAddress(n, l.logType), vertx, storageProvider)).get

      case l @ SendReadLogAdapterDescriptor(n, c, None) =>
        log(n).map(SendReadLogAdapter.props(logId(n, l.logType, Some(c)), _, eventbusAddress(n, l.logType), vertx, storageProvider)).get

      case l @ SendReadLogAdapterDescriptor(n, c, Some(b)) => ???

      case l @ ReliableReadLogAdapterDescriptor(n, c, None) =>
        log(n).map(ReliableReadLogAdapter.props(logId(n, l.logType, Some(c)), _, c, vertx, storageProvider)).get

      case l @ ReliableReadLogAdapterDescriptor(n, c, Some(b)) => ???

      case l @ WriteLogAdapterDescriptor(name) =>
        log(name).map(WriteLogAdapter.props(logId(name, l.logType), _, vertx)).get
    }
  }
}