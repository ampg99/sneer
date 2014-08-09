(ns sneer.server.router
  (:require
   [clojure.core.async :as async :refer [>! <! >!! <!! alts!! timeout]]
   [sneer.server.core :as core :refer [go-while-let]]))

(defn create-router [packets-in packets-out]
  "Store-and-Forward Server Protocol

The purpose of the temporary central server is to store
payloads (byte[]s) being sent from one peer to another in a
limited-size (100 elements?) FIFO queue and forward them as soon as
possible.  Clients must gracefully handle the server losing all its
state at any moment and restarting fresh.

Packets From Client to Server
ping()
Let's the server know the client is
online, when the client has no other useful packet to send. Keeps the
UDP connection open. Server replies with pong().

sendTo(Puk receiver, boolean reset, long sequence, byte[] payload)
If reset is true, the server will discard the entire queue of packets to
be sent to receiver, if any, and use sequence as the next expected
sequence number. The server does not expect any sequence number when it
starts, so the first packet sent from a peer to another must always have
reset true. If sequence is the next expected sequence number, adds
payload to the queue of payloads to be sent from the sender to receiver,
otherwise ignores the payload. Server replies with a statusOfQueues
packet for receiver (see below), even if sequence was wrong.

ack(Puk sender, long sequence)
See receiveFrom(...) below.

queryStatusOfQueues(Puk[] peers)
Server replies with a statusOfQueue packet for peers. See below.

Packets From Server to Client
pong()
Reply to ping() to indicate the server is alive.

receiveFrom(Puk sender, long sequence, byte[] payload)
Client replies with ack(sender, sequence);

statusOfQueues(StatusOfQueue[] statuses)
Indicates de status of the queues of payloads from this client to some
peers. StatusOfQueue is {Puk receiver, long highest-sequence-delivered,
long highest-sequence-to-send, boolean isFull}. The client can consider
all payloads up to highest-sequence-delivered as received by the
receiver. The client can consider all payloads up to
highest-sequence-to-send as received by the server and temporarily (the
server might crash and restart fresh) queued to be sent to
receiver. This packet is sent as a reply to queryStatusOfQueues(...), as
a reply to sendTo(...) and when the receiver
"
  (go-while-let
   [packet (<! packets-in)]
   (println "router/<!" packet)
   (>! packets-out {:intent :pong :to (:from packet)})))
