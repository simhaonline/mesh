package com.gentics.mesh.rest;

import static com.gentics.mesh.http.HttpConstants.APPLICATION_JSON;
import static com.gentics.mesh.http.HttpConstants.APPLICATION_JSON_UTF8;
import static org.apache.commons.lang3.StringUtils.isEmpty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.raml.model.MimeType;
import org.raml.model.Response;
import org.raml.model.parameter.QueryParameter;
import org.raml.model.parameter.UriParameter;

import com.gentics.mesh.json.JsonUtil;
import com.gentics.mesh.parameter.ParameterProvider;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.codegen.annotations.Nullable;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Route;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Simple wrapper for vert.x routes. The wrapper is commonly used to generate RAML descriptions for the route.
 */
public class Endpoint implements Route {

	private static final Logger log = LoggerFactory.getLogger(Endpoint.class);

	private Route route;

	private String displayName;

	private String description;

	/**
	 * Uri Parameters which map to the used path segments
	 */
	private Map<String, UriParameter> uriParameters = new HashMap<>();

	private Map<Integer, Response> exampleResponses = new HashMap<>();

	private String[] traits = new String[] {};

	private Object exampleRequest = null;

	private String pathRegex;

	private HttpMethod method;

	private String ramlPath;

	private final Set<String> consumes = new LinkedHashSet<>();
	private final Set<String> produces = new LinkedHashSet<>();

	private Map<String, QueryParameter> parameters = new HashMap<>();

	public Endpoint(Router router) {
		this.route = router.route();
	}

	public Route path(String path) {
		return route.path(path);
	}

	@Override
	public Route method(HttpMethod method) {
		if (this.method != null) {
			throw new RuntimeException(
					"The method for the endpoint was already set. The endpoint wrapper currently does not support more than one method per route.");
		}
		this.method = method;
		return route.method(method);
	}

	@Override
	public Route pathRegex(String path) {
		this.pathRegex = path;
		return route.pathRegex(path);
	}

	@Override
	public Route produces(String contentType) {
		produces.add(contentType);
		return route.produces(contentType);
	}

	@Override
	public Route consumes(String contentType) {
		consumes.add(contentType);
		return route.consumes(contentType);
	}

	@Override
	public Route order(int order) {
		return route.order(order);
	}

	@Override
	public Route last() {
		return route.last();
	}

	@Override
	public Route handler(Handler<RoutingContext> requestHandler) {
		validate();
		return route.handler(requestHandler);
	}

	/**
	 * Validate that all mandatory fields have been set.
	 */
	public void validate() {
		if (!produces.isEmpty() && exampleResponses.isEmpty()) {
			log.error("Endpoint {" + getRamlPath() + "} has no example response.");
			throw new RuntimeException("Endpoint {" + getRamlPath() + "} has no example responses.");
		}
		if ((consumes.contains(APPLICATION_JSON) || consumes.contains(APPLICATION_JSON_UTF8)) && exampleRequest == null) {
			log.error("Endpoint {" + getPath() + "} has no example request.");
			throw new RuntimeException("Endpoint has no example request.");
		}
		if (isEmpty(description)) {
			log.error("Endpoint {" + getPath() + "} has no description.");
			throw new RuntimeException("No description was set");
		}

		// Check whether all segments have a description.
		List<String> segments = getNamedSegments();
		for (String segment : segments) {
			if (!getUriParameters().containsKey(segment)) {
				throw new RuntimeException("Missing URI description for path {" + getRamlPath() + "} segment {" + segment + "}");
			}
		}

	}

	public List<String> getNamedSegments() {
		List<String> allMatches = new ArrayList<String>();
		Matcher m = Pattern.compile("\\{[^}]*\\}").matcher(getRamlPath());
		while (m.find()) {
			allMatches.add(m.group().substring(1, m.group().length() - 1));
		}
		return allMatches;
	}

	@Override
	public Route blockingHandler(Handler<RoutingContext> requestHandler) {
		return route.blockingHandler(requestHandler);
	}

	@Override
	public Route blockingHandler(Handler<RoutingContext> requestHandler, boolean ordered) {
		return route.blockingHandler(requestHandler, ordered);
	}

	@Override
	public Route failureHandler(Handler<RoutingContext> failureHandler) {
		return route.failureHandler(failureHandler);
	}

	@Override
	public Route remove() {
		return route.remove();
	}

	@Override
	public Route disable() {
		return route.disable();
	}

	@Override
	public Route enable() {
		return route.enable();
	}

	@Override
	public Route useNormalisedPath(boolean useNormalisedPath) {
		return route.useNormalisedPath(useNormalisedPath);
	}

	@Override
	public @Nullable String getPath() {
		return route.getPath();
	}

	/**
	 * Return the path used for RAML. If non null the path which was previously set using {@link #setRAMLPath(String)} will be returned. Otherwise the converted
	 * vert.x route path is returned. A vert.x path /:nodeUuid is converted to a RAML path /{nodeUuid}.
	 * 
	 * @return
	 */
	public String getRamlPath() {
		if (ramlPath == null) {
			return convertPath(route.getPath());
		}
		return ramlPath;
	}

	/**
	 * Convert the provided vertx path to a RAML path.
	 * 
	 * @param path
	 * @return
	 */
	private String convertPath(String path) {
		StringBuilder builder = new StringBuilder();
		String[] segments = path.split("/");
		for (int i = 0; i < segments.length; i++) {
			String segment = segments[i];
			if (segment.startsWith(":")) {
				segment = "{" + segment.substring(1) + "}";
			}
			builder.append(segment);
			if (i != segments.length - 1) {
				builder.append("/");
			}
		}
		if (path.endsWith("/")) {
			builder.append("/");
		}
		return builder.toString();
	}

	/**
	 * Set the endpoint display name.
	 * 
	 * @param name
	 * @return
	 */
	public Endpoint displayName(String name) {
		this.displayName = name;
		return this;
	}

	/**
	 * Set the endpoint description.
	 * 
	 * @param description
	 * @return
	 */
	public Endpoint description(String description) {
		this.description = description;
		return this;
	}

	/**
	 * Return the endpoint description.
	 * 
	 * @return
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * Return the display name for the endpoint.
	 * 
	 * @return
	 */
	public String getDisplayName() {
		return displayName;
	}

	/**
	 * Add the given response to the example responses.
	 * 
	 * @param status
	 *            Status code of the response
	 * @param description
	 *            Description of the response
	 * @return
	 */
	public Endpoint exampleResponse(HttpResponseStatus status, String description) {
		Response response = new Response();
		response.setDescription(description);
		exampleResponses.put(status.code(), response);
		return this;
	}

	/**
	 * Add the given response to the example responses.
	 * 
	 * @param code
	 *            Status code for the example response
	 * @param model
	 *            Model which will be turned into JSON
	 * @param description
	 *            Description of the example response
	 * @return
	 */
	public Endpoint exampleResponse(HttpResponseStatus status, Object model, String description) {
		Response response = new Response();
		response.setDescription(description);

		HashMap<String, MimeType> map = new HashMap<>();
		response.setBody(map);

		MimeType mimeType = new MimeType();
		String json = JsonUtil.toJson(model);
		mimeType.setExample(json);
		map.put("application/json", mimeType);

		exampleResponses.put(status.code(), response);
		return this;
	}

	/**
	 * Set the endpoint example request.
	 * 
	 * @param model
	 * @return
	 */
	public Endpoint exampleRequest(Object model) {
		this.exampleRequest = model;
		return this;
	}

	/**
	 * Set the traits information.
	 * 
	 * @param traits
	 * @return
	 */
	public Endpoint traits(String... traits) {
		this.traits = traits;
		return this;
	}

	/**
	 * Return the traits which were set for this endpoint.
	 * 
	 * @return
	 */
	public String[] getTraits() {
		return traits;
	}

	/**
	 * Return the map of example responses. The map contains examples per http status code.
	 * 
	 * @return
	 */
	public Map<Integer, Response> getExampleResponses() {
		return exampleResponses;
	}

	/**
	 * Return the endpoint http example request body.
	 * 
	 * @return
	 */
	public Object getExampleRequest() {
		return exampleRequest;
	}

	/**
	 * Return the vert.x route path regex.
	 * 
	 * @return
	 */
	public String getPathRegex() {
		return pathRegex;
	}

	/**
	 * Return the method used for the endpoint.
	 * 
	 * @return
	 */
	public HttpMethod getMethod() {
		return method;
	}

	/**
	 * Return the list of query parameters for the endpoint.
	 * 
	 * @return
	 */
	public Map<String, QueryParameter> getQueryParameters() {
		return parameters;
	}

	/**
	 * Add a query parameter provider to the endpoint. The query parameter provider will in turn provide examples, descriptions for all query parameters which
	 * the parameter provider provides.
	 * 
	 * @param clazz
	 */
	public void addQueryParameters(Class<? extends ParameterProvider> clazz) {
		try {
			ParameterProvider provider = clazz.newInstance();
			parameters.putAll(provider.getRAMLParameters());
		} catch (InstantiationException | IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/**
	 * Explicitly set the RAML path. This will override the path which is otherwise transformed using the vertx route path.
	 * 
	 * @param path
	 */
	public void setRAMLPath(String path) {
		this.ramlPath = path;
	}

	/**
	 * Return the uri parameters for the endpoint.
	 * 
	 * @return
	 */
	public Map<String, UriParameter> getUriParameters() {
		return uriParameters;
	}

	/**
	 * Add an uri parameter with description and example to the endpoint.
	 * 
	 * @param key
	 *            Key of the endpoint (e.g.: query, perPage)
	 * @param description
	 * @param example
	 */
	public void addUriParameter(String key, String description, String example) {
		UriParameter param = new UriParameter(key);
		param.setDescription(description);
		param.setExample(example);
		uriParameters.put(key, param);
	}

}