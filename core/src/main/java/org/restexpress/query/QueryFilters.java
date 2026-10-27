/*
 * Copyright 2011, Strategic Gains, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.restexpress.query;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.restexpress.Request;
import org.restexpress.common.query.FilterComponent;
import org.restexpress.common.query.FilterOperator;
import org.restexpress.common.query.QueryFilter;
import org.restexpress.common.util.StringUtils;
import org.restexpress.exception.BadRequestException;

/**
 * A factory for RestExpress-Common QueryFilter instance, parsing from a Request.
 * 
 * @author toddf
 * @since Apr 12, 2011
 * @see org.restexpress.common.query.QueryFilter
 */
public abstract class QueryFilters
{
	private static final String FILTER_REGEX = "(.+?):(.*?):(.+?)";
	private static final Pattern FILTER_PATTERN = Pattern.compile(FILTER_REGEX);
	private static final String FILTER_HEADER_NAME = "filter";
	private static final String FILTER_SEPARATOR = "\\|";
	
	
	// SECTION: FACTORY

	/**
	 * Create an instance of QueryFilter from the RestExpress request.
	 * Assumes all resource properties are filterable.
	 * 
	 * @param request the current request
	 */
	public static QueryFilter parseFrom(Request request)
	{
		return parseFrom(request, (List<String>) null);
	}

	/**
	 * Create an instance of QueryFilter from the RestExpress request, setting
	 * the appropriate properties that can be filtered.
	 * 
	 * @param request the current request
	 * @param allowedProperties an array of property names on which the resource can be filtered.
	 */
	public static QueryFilter parseFrom(Request request, String... allowedProperties)
	{
		return parseFrom(request, Arrays.asList(allowedProperties));
	}

	/**
	 * Create an instance of QueryFilter from the RestExpress request, setting
	 * the appropriate properties that can be filtered.
	 * 
	 * @param request the current request
	 * @param allowedProperties a list of property names on which the resource can be filtered.
	 */
	public static QueryFilter parseFrom(Request request, List<String> allowedProperties)
	{
		String filterString = request.getHeader(FILTER_HEADER_NAME);

		if (filterString == null || filterString.trim().isEmpty())
		{
			return new QueryFilter();
		}
		
		String[] nameValues = filterString.split(FILTER_SEPARATOR);

		if (nameValues == null || nameValues.length == 0)
		{
			return new QueryFilter();
		}

		List<FilterComponent> filters = new ArrayList<>(nameValues.length);

		for (String nameValue : nameValues)
		{
			Matcher m = FILTER_PATTERN.matcher(nameValue);

			if (m.matches())
			{
				String field = m.group(1);
				enforceSupportedProperties(allowedProperties, field);
				FilterOperator operator = findOperator(m.group(2));
				filters.add(new FilterComponent(field, operator, parse(operator, m.group(3))));
			}
		}

		return new QueryFilter(filters);
	}

	private static Object parse(FilterOperator operator, String group)
    {
		if (group == null || group.trim().isEmpty()) return null;

		if (FilterOperator.IN.equals(operator))
		{
			return group.split(",");
		}

		return group;
    }

	private static FilterOperator findOperator(String operation)
    {
		if (operation == null || "".equals(operation)) return FilterOperator.CONTAINS;

		String operator = operation.trim().toLowerCase();

		if ("=".equals(operator)) return FilterOperator.EQUALS;
		if ("!=".equals(operator)) return FilterOperator.NOT_EQUALS;
		if ("<".equals(operator)) return FilterOperator.LESS_THAN;
		if ("<=".equals(operator)) return FilterOperator.LESS_THAN_OR_EQUAL_TO;
		if (">".equals(operator)) return FilterOperator.GREATER_THAN;
		if (">=".equals(operator)) return FilterOperator.GREATER_THAN_OR_EQUAL_TO;
		if ("*".equals(operator)) return FilterOperator.STARTS_WITH;
		if ("in".equalsIgnoreCase(operator)) return FilterOperator.IN;

		return FilterOperator.EQUALS;
    }

	private static void enforceSupportedProperties(List<String> allowedProperties, String requested)
    {
	    if (allowedProperties != null && !allowedProperties.contains(requested))
	    {
	    	throw new BadRequestException(requested + " is not a supported filter. Supported filter names are: "
	    		+ StringUtils.join(", ", allowedProperties));
	    }
    }
}
