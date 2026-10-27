/*
    Copyright 2012, Strategic Gains, Inc.

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
package org.restexpress.common.query;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * @author toddf
 * @since Jul 27, 2012
 */
public class QueryFilterTest
{
	
	@Test
	public void shouldAddFilterCriteria()
	{
		QueryFilter f = new QueryFilter();
		assertFalse(f.hasFilters());
		f.addCriteria("test", FilterOperator.CONTAINS, "something");
		assertTrue(f.hasFilters());

		f.iterate(c -> {
			assertEquals("test", c.getField());
			assertEquals(FilterOperator.CONTAINS, c.getOperator());
			assertEquals("something", c.getValue());
		});
	}
}
