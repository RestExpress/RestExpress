package org.restexpress.route.index;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class PathSegment<T>
{
	private String segment;
	private Map<String, PathSegment<T>> childrenBySegment = new ConcurrentHashMap<>();
	private boolean isLeaf;

	// Populated on leaf nodes, this is the object being indexed by path name.
	private T indexedObject;

	public PathSegment(String segment)
	{
		this.segment = segment;
	}

	public PathSegment<T> addChild(PathSegment<T> child)
	{
		this.childrenBySegment.put(child.segment, child);
		return child;
	}

	public PathSegment<T> getChild(String segment)
	{
		return childrenBySegment.get(segment);
	}

	public boolean isLeaf()
	{
		return isLeaf;
	}

	public void setIsLeaf(boolean value)
	{
		this.isLeaf = value;
	}

	public boolean isIdentifierNode()
	{
		return false;
	}

	public boolean isSlashNode()
	{
		return false;
	}

	public void setIndexedObject(T o)
	{
		this.indexedObject = o;
	}

	public T getIndexedObject()
	{
		return indexedObject;
	}

	public boolean hasIndexedObject()
	{
		return (indexedObject != null);
	}

	@Override
	public String toString()
	{
		return String.valueOf(segment) + " (" + (isLeaf ? "leaf_node" : "internal_node") + ")";
	}
}
