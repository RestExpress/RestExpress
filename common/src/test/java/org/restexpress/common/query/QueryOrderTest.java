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
import static org.junit.Assert.fail;

import org.junit.Test;

/**
 * @author toddf
 * @since Jul 27, 2012
 */
public class QueryOrderTest
{
	
	@Test
	public void shouldAddSortCriteria()
	{
		QueryOrder o = new QueryOrder();
		assertFalse(o.isSorted());
		o.addSort("name", "-zip");
		assertTrue(o.isSorted());

		o.iterate(new OrderCallback()
		{
			int i = 0;

			@Override
			public void orderBy(OrderComponent component)
			{
				if (i == 0)
				{
					assertEquals("name", component.getFieldName());
					assertTrue(component.isAscending());
				}
				else if (i == 1)
				{
					assertEquals("zip", component.getFieldName());
					assertTrue(component.isDescending());
				}
				else
				{
					fail("Called too many times");
				}
				
				++i;
			}
		});
	}
}
