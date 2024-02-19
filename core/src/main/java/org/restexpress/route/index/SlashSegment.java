package org.restexpress.route.index;

class SlashSegment<T>
extends PathSegment<T>
{
	public static final String SLASH = "/";

	SlashSegment()
	{
		super(SLASH);
	}

	@Override
	public boolean isSlashNode()
	{
		return true;
	}
}
