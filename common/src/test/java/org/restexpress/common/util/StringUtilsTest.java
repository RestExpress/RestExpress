/*
    Copyright 2011, Strategic Gains, Inc.

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
package org.restexpress.common.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * @author toddf
 * @since Oct 7, 2011
 */
public class StringUtilsTest
{
	@Test
	public void shouldJoinListOfStrings()
	{
		List<String> objects = new ArrayList<String>();
		objects.add("Fredrich");
		objects.add("Todd");
		objects.add("Anthony");
		assertEquals("Fredrich, Todd, Anthony", StringUtils.join(", ", objects));
	}
	
	@Test
	public void shouldJoinFromSeparateItems()
	{
		assertEquals("this... is... great", StringUtils.join("... ", "this", "is", "great"));
	}
}
