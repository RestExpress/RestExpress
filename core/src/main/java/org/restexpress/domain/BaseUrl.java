package org.restexpress.domain;

/**
 * BaseUrl is a value object; a simple data structure for holding the components of a base URL.
 * 
 * @author Todd Fredrich
 * @since 19 Jan 2026
 * @see BaseUrlResolver
 */
public class BaseUrl
{
	protected String scheme = null;
	protected String host = null;
	protected String port = null;
	protected String prefix = null;

	@Override
	public String toString()
	{
		String defaultPort = scheme.equalsIgnoreCase("https") ? "443" : "80";
		String authority = host;

		if (port != null && !defaultPort.equals(port))
		{
			authority = host + ":" + port;
		}

		return String.format("%s://%s", scheme, authority) + (prefix != null ? prefix : "");
	}

	public boolean hasScheme()
	{
		return (scheme != null);
	}
	public boolean hasHost()
	{
		return (host != null);
	}

	public boolean hasPort()
	{
		return (port != null);
	}
}
