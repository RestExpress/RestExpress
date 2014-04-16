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
package org.restexpress.common.query;


/**
 * Supports the concept of 'pagination' via request 'Range' header or 'limit'
 * and 'offset' parameters.
 * <p/>
 * Paging is accomplished using the Range and Content-Range HTTP headers or
 * 'limit' and 'offset' query-string parameters.
 * <p/>
 * The client can request a range of results by including the "Range" header
 * with the request. For example, to get the first 25 results:
 * <p/>
 * GET /many_things.json<br/>
 * HTTP/1.1<br/>
 * Host: example.com<br/>
 * Range: items=0-24<br/>
 * <p/>
 * To request the same using the 'limit' and 'offset' parameters, limit would be
 * set to 25 with offset being set to 0 (or empty). For example, via the
 * query-string: &limit=25 which is equivalent to &limit=25&offset=0.
 * <p/>
 * When both 'Range' and 'limit' + 'offset are provided, the 'limit' and
 * 'offset' parameters override the 'Range' header. In other words, the
 * query-string parameters override the headers.
 * <p/>
 * The server will respond with a "Content-Range" header that includes the start
 * and end of the range, as well as a total count of all results. For example,
 * the response for the first 25 of 67 total results:
 * <p/>
 * HTTP/1.1 200 OK<br/>
 * Content-Type: application/json<br/>
 * Content-Range: items 0-24/67<br/>
 * 
 * @author toddf
 * @since Apr 11, 2011
 */
public class QueryRange
implements Cloneable
{
	// SECTION: INSTANCE VARIABLES

	private Long offset = null;
	private Integer limit = null;

	// SECTION: CONSTRUCTORS

	public QueryRange()
	{
		super();
	}

	public QueryRange(QueryRange that)
	{
		super();
		this.offset = that.offset;
		this.limit = that.limit;
	}

	public QueryRange(long offset, int limit)
	{
		super();
		setOffset(offset);
		setLimit(limit);
	}

	// SECTION: ACCESSORS / MUTATORS

	/**
	 * Returns an 'end' value calculated from the offset and limit values
	 * set on this QueryRange. If there is no limit, end is calculated to
	 * be the 'offset' value.
	 * 
	 * @return the computed end value of the range, or offset if there is no limit set.
	 */
	public long getEnd()
	{
		return (hasLimit() ? (getOffset() + getLimit() - 1) : getOffset());
	}

	/**
	 * Returns the limit value or zero if no limit is set.
	 * 
	 * @return the limit of this QueryRange, or zero.
	 */
	public int getLimit()
	{
		return (hasLimit() ? limit.intValue() : 0);
	}

	/**
	 * Answers whether a limit is set on this QueryRange.
	 * 
	 * @return true if a limit is set
	 */
	public boolean hasLimit()
	{
		return (limit != null);
	}

	/**
	 * Set the query limit, which represents the maximum number of results returned
	 * in a query.
	 * 
	 * @param value an integer >= zero
	 * @throws IllegalArgumentException if the limit is less-than zero
	 */
	public void setLimit(int value)
	{
		if (value < 0) throw new IllegalArgumentException("limit must be >= 0");

		this.limit = Integer.valueOf(value);
	}

	/**
	 * Sets the limit of this range by calculating the difference between
	 * the already-set offset and the given 'end' value.
	 *
	 * @param value
	 * @throws IllegalArgumentException if no offset is set or if end is less-than offset, which would cause a negative limit.
	 */
	public void setLimitViaEnd(long value)
	{
		if (!hasOffset()) throw new IllegalArgumentException("Setting 'end' requires 'offset' to be set first");

		setLimit((int) (value - getOffset() + 1));
	}

	/**
	 * getStart() is a synonym for getOffset().
	 */
	public long getStart()
	{
		return getOffset();
	}
	
	/**
	 * setStart() is a synonym for setOffset().
	 * 
	 * @param value
	 */
	public void setStart(long value)
	{
		setOffset(value);
	}

	public long getOffset()
	{
		return (hasOffset() ? offset.intValue() : 0);
	}

	public boolean hasOffset()
	{
		return (offset != null);
	}

	public void setOffset(long value)
	{
		if (value < 0) throw new IllegalArgumentException("offset must be >= 0");

		this.offset = Long.valueOf(value);
	}

	/**
	 * Returns true if setLimit(int) and setOffset(long) were both called successfully,
	 * or the constructor QueryRange(long, int) was successfully called.
	 * 
	 * @return true if both a limit and offset are set.
	 */
	public boolean isInitialized()
	{
		return hasLimit() && hasOffset();
	}

	/**
	 * Validates the range.
	 * 
	 * @return true if the range is valid
	 */
	public boolean isValid()
	{
		return (isInitialized()
			&& (getOffset() >= 0)
			&& (getLimit() >= 0));
	}

	@Override
	public String toString()
	{
		return assembleString().toString();
	}

	/**
	 * Creates a string in the form "items 0-24/66" using the values from this
	 * QueryRange along with the maximum number of items available. This value
	 * is suitable for setting the Content-Range header on the response from
	 * Range requests.
	 * <p/>
	 * No range checking is performed. It is therefore, the caller's
	 * responsibility to ensure that maxItems is greater-than the end value.
	 * 
	 * @param maxItems the maximum number of items available.
	 * @return a String of the form "items <first>-<last>/<max>"
	 */
	public String asContentRange(long maxItems)
	{
		return assembleString()
			.append("/")
			.append(maxItems)
			.toString();
	}

	private StringBuffer assembleString()
	{
		return new StringBuffer("items ")
			.append(getOffset())
			.append("-")
		    .append(getEnd());
	}

	// SECTION: COLLECTION SIZE RANGE CHECKING

	public boolean isOutside(int size, long count)
	{
		return (size == 0 && count > 0);
	}

    public boolean extendsBeyond(int size, long count)
    {
	    return (count == 0 && getEnd() > 0) || (size > 0 && getEnd() > (count - 1));
    }

    public boolean spans(int size, long count)
    {
	    return (size == count && count > 0);
    }

    public boolean isInside(int size, long count)
    {
	    return (size > 0 && getEnd() < count && !spans(size, count));
    }
    
    // SECTION: CLONEABLE
    
    @Override
    public QueryRange clone()
    {
    	try
        {
	        return (QueryRange) super.clone();
        }
        catch (CloneNotSupportedException e)
        {
	        e.printStackTrace();
        }
    	
    	return new QueryRange(this);
    }
}
