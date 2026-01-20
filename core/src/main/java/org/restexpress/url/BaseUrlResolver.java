/**
 * 
 */
package org.restexpress.url;

import org.restexpress.Request;
import org.restexpress.domain.Forwarded;
import org.restexpress.domain.ForwardedParseError;

/**
 * BaseUrlResolver is a utility class for resolving the base URL of a request,
 * considering various headers that may indicate the original request's scheme,
 * host, and port, especially in the presence of proxies or load balancers.
 * 
 * @author Todd Fredrich
 * @since 19 Jan 2026
 * @see BaseUrl
 */
public class BaseUrlResolver
{
	private BaseUrlResolver()
	{
		// Prevents instantiation.
	}

	/**
	 * Resolves the base URL from the given request, considering Forwarded, X-Forwarded-*, and Host headers.
	 * 
	 * @param request the request from which to resolve the base URL
	 * @return the resolved base URL as a BaseUrl object.
	 */
	public static BaseUrl resolve(Request request)
	{
		BaseUrl url = new BaseUrl();

		// Try Forwarded header first
		try
		{
			Forwarded forwarded = Forwarded.parse(request.getForwarded());
			String fullHost = forwarded.getFirstValue(Forwarded.HOST_TOKEN);
			url.host = Forwarded.getHostName(fullHost);
			url.scheme = forwarded.getFirstValue(Forwarded.PROTO_TOKEN);
			url.port = Forwarded.getHostPort(fullHost);
		}
		catch (ForwardedParseError e)
		{
			e.printStackTrace();
		}

		// Fallback to X-Forwarded-* headers
		if (!url.hasScheme())
		{
			url.scheme = getXForwardedProto(request);
		}

		if (!url.hasHost())
		{
			url.host = getXForwardedHost(request);
		}
	
		if (!url.hasPort())
		{
			url.port = getXForwardedPort(request);
		}

		url.prefix = request.getHeader("X-Forwarded-Prefix");

		// Fallback to request values
		if (!url.hasScheme())
		{
			url.scheme = request.getScheme();
		}

		if (!url.hasHost())
		{
			String host = request.getHost();
			
			if (host != null && host.contains(":"))
			{
				url.host = host.substring(0, host.indexOf(":"));
				url.port = host.substring(host.indexOf(":") + 1);
			}
			else
			{
				url.host = host;
			}
		}

		return url;
	}

	private static String getXForwardedPort(Request request)
	{
		return request.getHeader("X-Forwarded-Port");
	}

	private static String getXForwardedHost(Request request)
	{
		return request.getHeader("X-Forwarded-Host");
	}

	private static String getXForwardedProto(Request request)
	{
		return request.getHeader("X-Forwarded-Proto");
	}
}
