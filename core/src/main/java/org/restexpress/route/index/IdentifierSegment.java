package org.restexpress.route.index;
import java.util.regex.Pattern;

class IdentifierSegment<T>
extends PathSegment<T>
{
	public static final String WILDCARD = "{**}";
	private static final String IDENTIFIER_REGEX = "\\{\\w+?\\}";
	private static final Pattern IDENTIFIER_PATTERN = Pattern.compile(IDENTIFIER_REGEX);

	private String parameterName;

	public IdentifierSegment(String parameterName)
	{
		super(WILDCARD);
		this.parameterName = parameterName;
	}

	@Override
	public boolean isIdentifierNode()
	{
		return true;
	}

	public String getParameterName()
	{
		return parameterName;
	}

	@Override
	public String toString()
	{
		return parameterName;
	}

	public static boolean isIdentifier(String segment)
	{
		return IDENTIFIER_PATTERN.matcher(segment).matches();
	}
}
