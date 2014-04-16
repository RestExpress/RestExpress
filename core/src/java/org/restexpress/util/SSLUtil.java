package org.restexpress.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

public class SSLUtil
{
	public static SSLContext loadContext(String keyStore,
			String filePassword, String keyPassword) throws Exception
	{
		FileInputStream fin = null;

		try
		{
			KeyStore ks = KeyStore.getInstance("JKS");
			fin = new FileInputStream(keyStore);
			ks.load(fin, filePassword.toCharArray());

			KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
			kmf.init(ks, keyPassword.toCharArray());

			SSLContext context = SSLContext.getInstance("TLS");
			context.init(kmf.getKeyManagers(), null, null);
			return context;
		}
		finally
		{
			if (null != fin)
			{
				try
				{
					fin.close();
				}
				catch (IOException e)
				{
				}
			}
		}
	}

}
