package org.restexpress.route;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;

import org.junit.BeforeClass;
import org.junit.Test;
import org.restexpress.Request;
import org.restexpress.Response;
import org.restexpress.settings.RouteDefaults;

import io.netty.handler.codec.http.HttpMethod;

/**
 * @author toddf
 * @since Sep 27, 2010
 */
public class RouteDeclarationTest
{
    private static final String RAH_ROUTE_NAME = "POST_ONLY";
	private static RouteDeclaration routeDeclarations;
    private static RouteMapping routeMapping;
	
	@BeforeClass
	public static void setUpBeforeClass()
	throws Exception
	{
		routeDeclarations = new Routes();
		((Routes) routeDeclarations).defineRoutes();
		routeMapping = routeDeclarations.createRouteMapping(new RouteDefaults());
	}

	@Test
	public void testGetNullNamedRoute()
	{
		Route r = routeMapping.getNamedRoute(null, HttpMethod.GET);
		assertNull(r);
	}

	@Test
	public void shouldReturnNullForNamedRouteWithWrongMethod()
	{
		Route r = routeMapping.getNamedRoute(RAH_ROUTE_NAME, HttpMethod.GET);
		assertNull(r);
	}

	@Test
	public void shouldGetReplyToNamedRoute()
	{
		Route r = routeMapping.getNamedRoute(RAH_ROUTE_NAME, HttpMethod.POST);
		assertNotNull(r);
		assertEquals(RAH_ROUTE_NAME, r.getName());
		assertEquals("createRah", r.getAction().getName());
	}

	@Test
	public void shouldGetCRUDReadNamedRoute()
	{
		Route r = routeMapping.getNamedRoute("CRUD_ROUTE", HttpMethod.GET);
		assertNotNull(r);
		assertEquals("CRUD_ROUTE", r.getName());
		assertEquals("read", r.getAction().getName());
	}

	@Test
	public void shouldGetCRUDCreateNamedRoute()
	{
		Route r = routeMapping.getNamedRoute("CRUD_ROUTE", HttpMethod.POST);
		assertNotNull(r);
		assertEquals("CRUD_ROUTE", r.getName());
		assertEquals("create", r.getAction().getName());
	}

	@Test
	public void shouldGetCRUDUpdateNamedRoute()
	{
		Route r = routeMapping.getNamedRoute("CRUD_ROUTE", HttpMethod.PUT);
		assertNotNull(r);
		assertEquals("CRUD_ROUTE", r.getName());
		assertEquals("update", r.getAction().getName());
	}

	@Test
	public void shouldGetCRUDDeleteNamedRoute()
	{
		Route r = routeMapping.getNamedRoute("CRUD_ROUTE", HttpMethod.DELETE);
		assertNotNull(r);
		assertEquals("CRUD_ROUTE", r.getName());
		assertEquals("delete", r.getAction().getName());
	}

	@Test
	public void shouldFindGetMethod()
	{
		List<HttpMethod> methods = routeMapping.getAllowedMethods("/foo/bar/42.json");
		assertNotNull(methods);
		assertEquals(1, methods.size());
		assertEquals(HttpMethod.GET, methods.get(0));
	}

	@Test
	public void shouldFindPostMethod()
	{
		List<HttpMethod> methods = routeMapping.getAllowedMethods("/foo.json");
		assertNotNull(methods);
		assertEquals(1, methods.size());
		assertEquals(HttpMethod.POST, methods.get(0));
	}
	
	@Test
	public void shouldFindMultipleMethods()
	{
		List<HttpMethod> methods = routeMapping.getAllowedMethods("/foo/foo42.json");
		assertNotNull(methods);
		assertEquals(4, methods.size());
		assertTrue(methods.contains(HttpMethod.GET));
		assertTrue(methods.contains(HttpMethod.PUT));
		assertTrue(methods.contains(HttpMethod.POST));
		assertTrue(methods.contains(HttpMethod.DELETE));
	}

	private static class Routes
	extends RouteDeclaration
	{
		private InnerService service;
		private RouteDefaults defaults = new RouteDefaults();
		
		public Routes()
		{
			super();
			service = new InnerService();
		}

        public void defineRoutes()
        {
    		uri("/foo/bar/{barId}.{format}", service, defaults)
    			.action("readBar", HttpMethod.GET);

    		uri("/foo/bat/{batId}.{format}", service, defaults)
    			.action("readBat", HttpMethod.GET);

    		uri("/foo.{format}", service, defaults)
    			.method(HttpMethod.POST);

    		uri("/foo/{fooId}.{format}", service, defaults)
    			.name("CRUD_ROUTE");

    		uri("/foo/rah/{rahId}.{format}", service, defaults)
    			.action("createRah", HttpMethod.POST)
    			.name(RAH_ROUTE_NAME);

    		uri("/foo/yada/{yadaId}.{format}", service, defaults)
    			.action("readYada", HttpMethod.GET);
        }
	}
	
	private static class InnerService
	{
		@SuppressWarnings("unused")
        public Object create(Request request, Response response)
		{
			return null;
		}

		@SuppressWarnings("unused")
        public Object read(Request request, Response response)
		{
			return null;
		}

		@SuppressWarnings("unused")
        public void update(Request request, Response response)
		{
		}

		@SuppressWarnings("unused")
		public void delete(Request request, Response response)
		{
		}

		@SuppressWarnings("unused")
		public Object createRah(Request request, Response response)
		{
			return null;
		}

		@SuppressWarnings("unused")
		public Object readBar(Request request, Response response)
		{
			return null;
		}

		@SuppressWarnings("unused")
		public Object readBat(Request request, Response response)
		{
			return null;
		}

		@SuppressWarnings("unused")
		public Object readYada(Request request, Response response)
		{
			return null;
		}
	}
}
