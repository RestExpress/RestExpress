package org.restexpress.route.index;

import static org.restexpress.route.index.SlashSegment.SLASH;

import java.util.Arrays;

public class PathIndex<T>
{
	private PathSegment<T> root;

	public PathIndex()
	{
		super();
		this.root = new SlashSegment<>();
	}

	public void insert(String path, T object)
	{
		String[] pathSegments = asPathSegments(path);
		PathSegment<T> currentNode = root;

		for (String segment : pathSegments)
		{
			// This accounts for a leading slash ('/') on the path that creates an empty segment in the array.
			if (segment.isBlank()) continue;

			currentNode = getOrCreateSlashNode(currentNode);

			if (isIdentifier(segment))
			{
				currentNode = insertIdentifierSegment(currentNode, segment);
			}
			else
			{
				currentNode = insertPathSegment(currentNode, segment);
			}
		}

		currentNode.setIsLeaf(true);
		currentNode.setIndexedObject(object);
	}

	public SearchResults<T> search(String path)
	{
		String[] pathSegments = asPathSegments(path);
		PathSegment<T> currentNode = root;
		SearchResults<T> results = new SearchResults<>(path);

		for (String segment : Arrays.asList(pathSegments))
		{
			// This accounts for a leading slash ('/') on the path that creates an empty segment in the array.
			if (segment.isBlank()) continue;

			currentNode = traverse(currentNode, segment);

			if (currentNode != null)
			{
				if (currentNode.isIdentifierNode())
				{
					results.addIdentifier(((IdentifierSegment<T>) currentNode).getParameterName(), segment);
				}
				else
				{
					results.addSegment(segment);
				}
			}
			else
			{
				results.setFailed();
				return results;
			}
		}

		if (currentNode == null || !currentNode.isLeaf())
		{
			results.setFailed();
		}
		else if (currentNode.hasIndexedObject())
		{
			results.setObject(currentNode.getIndexedObject());
		}

		return results;
	}

	private PathSegment<T> traverse(PathSegment<T> node, String pathSegment)
	{
		if (node == null || pathSegment == null) return null;

		// If the current node is a slash node, move to its child node
		if (!node.isSlashNode())
	    {
	    	node = node.getChild(SLASH);
	    	if (node == null) return null;
	    }

		 // Find the child node that matches the segment
	    PathSegment<T> childNode = node.getChild(pathSegment);


	    // If there is not a segment match attempt to find an identifier segment
	    return childNode != null ? childNode : node.getChild(IdentifierSegment.WILDCARD);
	}

	private String[] asPathSegments(String path)
	{
		return path.split(SLASH);
	}

	private boolean isIdentifier(String segment)
	{
		return IdentifierSegment.isIdentifier(segment);
	}

	private PathSegment<T> insertIdentifierSegment(PathSegment<T> currentNode, String pathSegment)
	{
		IdentifierSegment<T> childNode = (IdentifierSegment<T>) currentNode.getChild(IdentifierSegment.WILDCARD);

		if (childNode == null)
		{
			childNode = new IdentifierSegment<>(pathSegment);
			currentNode.addChild(childNode);
		}

		return childNode;
	}

	private PathSegment<T> insertPathSegment(PathSegment<T> currentNode, String pathSegment)
	{
		PathSegment<T> childNode = currentNode.getChild(pathSegment);

		if (childNode == null)
		{
			childNode = currentNode.addChild(new PathSegment<>(pathSegment));
		}

		return childNode;
	}

	private PathSegment<T> getOrCreateSlashNode(PathSegment<T> currentNode)
	{
		if (!currentNode.isSlashNode())
		{
			PathSegment<T> slashNode = currentNode.getChild(SLASH);

			if (slashNode != null)
			{
				return slashNode;
			}
			else
			{
				return currentNode.addChild(new SlashSegment<>());
			}
		}

		return currentNode;
	}
}
