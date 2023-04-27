package oam.security.config;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @description 自定义AuthenticationSuccessHandler
 * @author Holisun Wu
 * @time 2017年07月03日 上午09:39:45
 */
public class AuthenticationFailureHandlerNeo extends SimpleUrlAuthenticationFailureHandler
{
	@Override
	public void onAuthenticationFailure(final HttpServletRequest request, final HttpServletResponse response, final AuthenticationException exception) throws IOException, ServletException
	{
		final String jsonString = "{\"value\":\"" + "Fail" + "\"}";
		final JsonNode responseJsonNode = (new ObjectMapper()).readTree(jsonString);
		response.setContentType(MediaType.APPLICATION_JSON_UTF8_VALUE);
		response.getWriter().print(responseJsonNode.toString());
		response.getWriter().flush();
	}
}
