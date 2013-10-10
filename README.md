Intoduction
=========

Client &amp; server communication utility based on java nio.

It is mainly design for send commands and notifications to clients to do some management job or something like that, not for high performence client&server communication like [Netty](http://netty.io/) or [mina](http://mina.apache.org/).


How it works
==========
Client and server will keep connection until server send a command or notication to one or all clients. When the server send a command or notification, it will close the connection between itself and the client. Then the client will parse the command or notification, to do some specific job and finally it will reconnect to the server to wait for next command or notification.

