package org.restexpress.route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import org.restexpress.contenttype.MediaRange;

import io.netty.handler.codec.http.HttpMethod;

public class RouteIndexNode
{
	private static final String SLASH = "/";
	private static final String WILDCARD = "*";

	// Finds parameter wild cards in the URL pattern string.
	private static final String URL_PARAM_REGEX = "\\{\\w*?\\}";

	// Pattern to match URL pattern parameter names.
	private static final Pattern URL_PARAM_PATTERN = Pattern.compile(URL_PARAM_REGEX);

	private String segment;

	// populated on leaf nodes to indicate the methods, content-type and full path pattern.
	private List<Route> routes;
	private Map<String, RouteIndexNode> childrenBySegment;

	public RouteIndexNode(String segment)
	{
		super();
		this.segment = (isParameter(segment) ? WILDCARD : segment);
	}

	public RouteIndexNode add(Route route)
	{
		String[] segments = getSegments(route.getPattern());
		addChild(route, segments, 0);
		return this;
	}

	public Route find(HttpMethod method, String path, MediaRange contentType)
	{
		String[] segments = getSegments(path);
		RouteIndexNode node = traverse(segments, 0);

		if (node == null) return null;

		return node.findRoute(method, contentType);
	}

	public List<Route> find(String path)
	{
		String[] segments = getSegments(path);
		RouteIndexNode node = traverse(segments, 0);

		if (node == null) return Collections.emptyList();

		return Collections.unmodifiableList(node.routes);
	}

	@Override
	public String toString()
	{
		return SLASH + segment;
	}

	private RouteIndexNode traverse(String[] segments, int i)
	{
		if (i < segments.length)
		{
			RouteIndexNode child = getChild(segments[i]);

			if (child == null) return null;

			if (isLeafNode(segments.length, i))
			{
				return child;
			}

			return child.traverse(segments, i + 1);
		}

		return null;
	}

	private void addChild(Route route, String[] segments, int i)
	{
		if (i < segments.length)
		{
			RouteIndexNode child = getChild(segments[i]);

			if (child == null)
			{
				child = new RouteIndexNode(segments[i]);
				ensureChildrenCollection();
				childrenBySegment.put(child.segment, child);
			}

			if (isLeafNode(segments.length, i))
			{
				child.addEndpoint(route);
				return;
			}

			child.addChild(route, segments, i + 1);
		}
	}

	private String[] getSegments(String path)
	{
		if (path.startsWith(SLASH)) return path.substring(1).split(SLASH);
		return path.split(SLASH);
	}

	private boolean isParameter(String segment)
	{
		return URL_PARAM_PATTERN.matcher(segment).matches();
	}

	private boolean isLeafNode(int segmentCount, int i)
	{
		return (i == (segmentCount - 1));
	}

	private void ensureChildrenCollection()
	{
		if (childrenBySegment == null)
		{
			childrenBySegment = new TreeMap<>();
		}
	}

	private RouteIndexNode getChild(String segment)
	{
		if (childrenBySegment == null) return null;

		RouteIndexNode child = childrenBySegment.get(segment);

		if (child != null) return child;

		return childrenBySegment.get(WILDCARD);
	}

	private void addEndpoint(Route route)
	{
		if (routes == null)
		{
			routes = new ArrayList<>();
		}

		routes.add(route);
	}

	private Route findRoute(HttpMethod method, MediaRange contentType)
	{
		Optional<Route> sel = routes.stream().filter(s -> s.appliesTo(method, contentType)).limit(1).findFirst();
		return (sel.isPresent() ? sel.get() : null);
	}
}
