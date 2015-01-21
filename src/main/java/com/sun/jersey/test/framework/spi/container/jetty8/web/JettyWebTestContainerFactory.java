/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
 * Portions Copyright 2012 Juha Heljoranta
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * http://glassfish.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package com.sun.jersey.test.framework.spi.container.jetty8.web;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.EventListener;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.Servlet;
import javax.ws.rs.core.UriBuilder;

import org.eclipse.jetty.annotations.AnnotationConfiguration;
import org.eclipse.jetty.plus.webapp.PlusConfiguration;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.test.framework.AppDescriptor;
import com.sun.jersey.test.framework.WebAppDescriptor;
import com.sun.jersey.test.framework.spi.container.TestContainer;
import com.sun.jersey.test.framework.spi.container.TestContainerException;
import com.sun.jersey.test.framework.spi.container.TestContainerFactory;
import org.eclipse.jetty.webapp.Configuration;
import org.eclipse.jetty.webapp.WebAppContext;

/**
 * A Web-based test container factory for creating test container instances
 * using Jetty 8.
 * 
 * <p>
 * Adapted from
 * {@linkplain com.sun.jersey.test.framework.spi.container.grizzly2.web.GrizzlyWebTestContainerFactory}
 * .
 * 
 * @author Juha Heljoranta
 */
public class JettyWebTestContainerFactory implements TestContainerFactory {

	@SuppressWarnings("unchecked")
	public Class<WebAppDescriptor> supports() {
		return WebAppDescriptor.class;
	}

	public TestContainer create(URI baseUri, AppDescriptor ad) {
		if (!(ad instanceof WebAppDescriptor))
			throw new IllegalArgumentException(
					"The application descriptor must be an instance of WebAppDescriptor");

		return new JettyWebTestContainer(baseUri, (WebAppDescriptor) ad);
	}

	private static class JettyWebTestContainer implements TestContainer {

		private final Logger log = Logger.getLogger(JettyWebTestContainer.class
				.getName());

		final URI baseUri;

		final String contextPath;

		final String servletPath;

		final Class<? extends Servlet> servletClass;

		List<WebAppDescriptor.FilterDescriptor> filters = null;

		final List<Class<? extends EventListener>> eventListeners;

		final Map<String, String> initParams;

		final Map<String, String> contextParams;

		private Server server;

		private JettyWebTestContainer(URI baseUri, WebAppDescriptor ad) {
			this.baseUri = UriBuilder.fromUri(baseUri)
					.path(ad.getContextPath()).path(ad.getServletPath())
					.build();

			log.info("Creating Jetty Web Container configured at the base URI "
					+ this.baseUri);
			this.contextPath = ad.getContextPath();
			this.servletPath = ad.getServletPath();
			this.servletClass = ad.getServletClass();
			this.filters = ad.getFilters();
			this.initParams = ad.getInitParams();
			this.contextParams = ad.getContextParams();
			this.eventListeners = ad.getListeners();

			instantiateJettyWebServer();

		}

		public Client getClient() {
			return null;
		}

		public URI getBaseUri() {
			return baseUri;
		}

		public void start() {
			log.info("Starting the Jetty Web Container...");
			try {
				server.start();
			} catch (Exception e) {
				throw new TestContainerException(e);
			}

		}

		public void stop() {
			log.info("Stopping the Jetty Web Container...");
			try {
				server.stop();
			} catch (Exception e) {
				throw new TestContainerException(e);
			}
		}

		private void instantiateJettyWebServer() {

			String contextPathLocal;
			if (contextPath != null && contextPath.length() > 0) {
				if (!contextPath.startsWith("/")) {
					contextPathLocal = "/" + contextPath;
				} else {
					contextPathLocal = contextPath;
				}
			} else {
				contextPathLocal = "";
			}
			String servletPathLocal;
			if (servletPath != null && servletPath.length() > 0) {
				if (!servletPath.startsWith("/")) {
					servletPathLocal = "/" + servletPath;
				} else {
					servletPathLocal = servletPath;
				}
				if (servletPathLocal.endsWith("/")) {
					servletPathLocal += "*";
				} else {
					servletPathLocal += "/*";
				}
			} else {
				servletPathLocal = "/*";
			}

			WebAppContext context = new WebAppContext();

			context.setDisplayName("TestContext");
			context.setContextPath(contextPathLocal);
			context.setConfigurations(new Configuration[]{
					new PlusConfiguration(),
					new AnnotationConfiguration(),
			});

			if (servletClass != null) {
				ServletHolder holder = new ServletHolder(
						servletClass.getName(), servletClass);
				for (String initParamName : initParams.keySet()) {
					holder.setInitParameter(initParamName,
							initParams.get(initParamName));
				}
				context.addServlet(holder, servletPathLocal);
			} else {
				context.addServlet(DefaultServlet.class, servletPathLocal);
				// ServletHolder holder = new ServletHolder("default",
				// new HttpServlet() {
				// public void service() throws ServletException {
				// }
				// });
				// context.addServlet(holder, "");
			}

			for (Class<? extends EventListener> eventListener : eventListeners) {
				try {
					context.addEventListener(eventListener.newInstance());
				} catch (Exception e) {
					throw new TestContainerException(e);
				}
			}

			for (String contextParamName : contextParams.keySet()) {
				context.setInitParameter(contextParamName,
						contextParams.get(contextParamName));
			}

			// Filter support
			if (filters != null) {
				for (WebAppDescriptor.FilterDescriptor d : this.filters) {
					FilterHolder fh = new FilterHolder(d.getFilterClass());
					fh.setName(d.getFilterName());
					if (d.getInitParams() != null) {
						fh.setInitParameters(d.getInitParams());
					}
					context.addFilter(fh, servletPathLocal, null);
				}
			}

			try {
				final String host = (baseUri.getHost() == null) ? "0.0.0.0"
						: baseUri.getHost();
				final int port = (baseUri.getPort() == -1) ? 80 : baseUri
						.getPort();
				server = new Server(new InetSocketAddress(host, port));
				server.setHandler(context);
			} catch (Exception ioe) {
				throw new TestContainerException(ioe);
			}
		}
	}

}
