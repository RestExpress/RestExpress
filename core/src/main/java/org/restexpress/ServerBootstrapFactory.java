/*
    Copyright 2015, Strategic Gains, Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/
package org.restexpress;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.concurrent.Future;

/**
 * @author toddf
 * @since Jul 10, 2015
 */
public class ServerBootstrapFactory
{
	private EventLoopGroup bossGroup;
	private EventLoopGroup workerGroup;

	public ServerBootstrap newServerBootstrap(int ioThreadCount)
    {
		if (Epoll.isAvailable())
		{
			return newEpollServerBootstrap(ioThreadCount);
		}

		return newNioServerBootstrap(ioThreadCount);
    }

	public void shutdownGracefully(boolean shouldWait)
    {
		Future<?> workerFuture = workerGroup.shutdownGracefully();
		Future<?> bossFuture = bossGroup.shutdownGracefully();

		if (shouldWait)
		{
			workerFuture.awaitUninterruptibly();
			bossFuture.awaitUninterruptibly();
		}
    }

	private ServerBootstrap newNioServerBootstrap(int ioThreadCount)
    {
	    if (ioThreadCount > 0)
		{
			bossGroup = new NioEventLoopGroup(ioThreadCount);
			workerGroup = new NioEventLoopGroup(ioThreadCount);
		}
		else
		{
			bossGroup = new NioEventLoopGroup();
			workerGroup = new NioEventLoopGroup();
		}

		return new ServerBootstrap()
			.group(bossGroup, workerGroup)
			.channel(NioServerSocketChannel.class);
    }

	private ServerBootstrap newEpollServerBootstrap(int ioThreadCount)
    {
	    if (ioThreadCount > 0)
	    {
	    	bossGroup = new EpollEventLoopGroup(ioThreadCount);
	    	workerGroup = new EpollEventLoopGroup(ioThreadCount);
	    }
	    else
	    {
	    	bossGroup = new EpollEventLoopGroup();
	    	workerGroup = new EpollEventLoopGroup();
	    }

	    return new ServerBootstrap()
	    	.group(bossGroup, workerGroup)
	    	.channel(EpollServerSocketChannel.class);
    }
}
