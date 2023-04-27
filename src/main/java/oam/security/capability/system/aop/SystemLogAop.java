package oam.security.capability.system.aop;

import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import oam.security.capability.system.aop.dto.ApiLogDto;
import oam.security.capability.system.aop.util.AopUtil;
import oam.security.controller.dto.ControllerBaseDto;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import lombok.extern.slf4j.Slf4j;
import oam.security.config.security.domain.JwtUser;

/**
 * 針對指定的使用者操作指令，進行資料源切換與發送日誌。這些指令全部都是在 *Controller 的方法
 *
 * @author Holisun Wu
 */
@Component
@Aspect
@Slf4j
public class SystemLogAop
{
	@Autowired
	private ObjectMapper	objectMapper;

	/**
	 * 準備使用者操作日誌
	 *
	 * @param joinPoint
	 * @param jwtUser
	 * @return
	 */
	private ApiLogDto prepareUserOperationLog(final ProceedingJoinPoint joinPoint, final JwtUser jwtUser)
	{
		final ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		final HttpServletRequest request = attributes.getRequest();
		final String requestQueryString = (request.getQueryString() == null) ? "" : "?" + request.getQueryString();
		final String requestUri = request.getRequestURI() + requestQueryString;
		final String operationName = joinPoint.getSignature().getName();
		final HttpServletResponse response = attributes.getResponse();
		final ApiLogDto apiLogDto = new ApiLogDto();
		// apiLogDto.setTenantId(jwtUser.getTenantId());
		// log.debug("={}, ={}", jwtUser.getId(), jwtUser.getUsername());
		apiLogDto.setUserId(jwtUser.getId());
		apiLogDto.setUsername(jwtUser.getUsername());
		apiLogDto.setApiType(ApiLogDto.ApiType.SystemLog);
		apiLogDto.setOperationName(operationName);
		apiLogDto.setOperationTag(getOperationTag(joinPoint));
		apiLogDto.setRequestMethod(request.getMethod());
		apiLogDto.setRequestUri(requestUri);
		// 這裡取得的 status code 固定是 200，與實際的傳回值 201/204 不同，所以後續需要修正
		apiLogDto.setResponseStatusCode(response.getStatus());
		final Boolean isOperationNameMatched = false;

		// 當 POST 不是用來新增時，而是表示某種操作時，需做以下修正
		final List<String> parameterNames = Arrays.asList(AopUtil.getParameterNames(joinPoint));
		if (apiLogDto.getRequestMethod().equalsIgnoreCase("POST"))
		{
			if (!operationName.startsWith("create"))
			{
				apiLogDto.setTargetId(AopUtil.getFirstParameterValueAsString(joinPoint));
				apiLogDto.setResponseStatusCode(HttpStatus.NO_CONTENT.value());
				if (parameterNames.contains("requestJson"))
				{
					apiLogDto.setOperationInput((JsonNode) AopUtil.getParameterValue(joinPoint, "requestJson"));
				} else if (parameterNames.contains("dto"))
				{
					apiLogDto.setOperationInput(getJsonNodeFromDto(joinPoint, "dto"));
				}
				return apiLogDto;
			}
		}

		// 依據方法類型設定屬性
		switch (apiLogDto.getRequestMethod())
		{
			case "GET" :
				// apiLogDto.setTargetId(AopUtil.getFirstParameterValueAsString(joinPoint));
				// apiLogDto.setResponseStatusCode(HttpStatus.OK.value());
				break;
			case "POST" :
				if (parameterNames.contains("json"))
				{
					apiLogDto.setOperationInput((JsonNode) AopUtil.getParameterValue(joinPoint, "json"));
				} else if (parameterNames.contains("requestJson"))
				{
					apiLogDto.setOperationInput((JsonNode) AopUtil.getParameterValue(joinPoint, "requestJson"));
				} else if (parameterNames.contains("dto"))
				{
					apiLogDto.setOperationInput(getJsonNodeFromDto(joinPoint, "dto"));
				}
				apiLogDto.setResponseStatusCode(HttpStatus.CREATED.value());
				break;
			case "PUT" :
				if (parameterNames.contains("json"))
				{
					apiLogDto.setOperationInput((JsonNode) AopUtil.getParameterValue(joinPoint, "json"));
				} else if (parameterNames.contains("requestJson"))
				{
					apiLogDto.setOperationInput((JsonNode) AopUtil.getParameterValue(joinPoint, "requestJson"));
				} else if (parameterNames.contains("dto"))
				{
					apiLogDto.setOperationInput(getJsonNodeFromDto(joinPoint, "dto"));
				}
				apiLogDto.setTargetId(AopUtil.getFirstParameterValueAsString(joinPoint));
				apiLogDto.setResponseStatusCode(HttpStatus.NO_CONTENT.value());
				break;
			case "DELETE" :
				apiLogDto.setOperationInput(getJsonNodeFromDto(joinPoint, "requestJsonNode"));
				apiLogDto.setTargetId(AopUtil.getFirstParameterValueAsString(joinPoint));
				apiLogDto.setResponseStatusCode(HttpStatus.NO_CONTENT.value());
				break;
			default :
				break;
		}

		// ---[ 依據方法名稱設定屬性 ]-------------------------------------------------------------------------------------------[S]
		switch (operationName)
		{
			case "createFileServer" :
			case "modifyFileServer" :
				// 遮蔽密碼欄位
				final ObjectNode operationInput = (ObjectNode) apiLogDto.getOperationInput();
				final JsonNode password = operationInput.findValue("password");
				if (password.isMissingNode() == false && password.isNull() == false) operationInput.put("password", password.asText().replaceAll(".", "*"));
				break;
			default :
				break;
		}
		// ---[ 依據方法名稱設定屬性 ]-------------------------------------------------------------------------------------------[E]

		return apiLogDto;
	}

//	/**
//	 * 發送使用者操作日誌
//	 *
//	 * @param joinPoint
//	 * @param jwtUser
//	 * @param operationOutput
//	 * @throws AmqpException
//	 * @throws JsonProcessingException
//	 * @throws InterruptedException
//	 * @throws ExecutionException
//	 * @throws TimeoutException
//	 */
//	@Async
//	private void sendUserOperationLog(final ProceedingJoinPoint joinPoint, final JwtUser jwtUser, final Object operationOutput)
//			throws AmqpException, JsonProcessingException, InterruptedException, ExecutionException, TimeoutException
//	{
//		final ApiLogDto apiLogDto = prepareUserOperationLog(joinPoint, jwtUser);
//		// 決定哪些 operation 不送日誌
//		if (isOperationIgnore(apiLogDto)) return;
//
//		final String operationName = apiLogDto.getOperationName();
//		final ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
//		final HttpServletRequest request = attributes.getRequest();
//
//		// ---[ 發送日誌 ]---------------------------------------------------------------------------------------------[S]
//		if (!request.getMethod().equals("GET") && operationOutput != null && operationOutput instanceof JsonNode)
//		{
//			apiLogDto.setOperationOutput((JsonNode) operationOutput);
//		}
//
//		if (request.getMethod().equals("POST") && operationName.startsWith("create") && operationOutput != null && operationOutput instanceof JsonNode)
//		{
//			final JsonNode jsonResult = (JsonNode) operationOutput;
//			apiLogDto.setTargetId(jsonResult.path("id").asText());
//		}
//		apiLogDto.setIsSuccessful(true);
//		this.amqpService.sendMsgForOperation(apiLogDto);
//		// ---[ 發送日誌 ]---------------------------------------------------------------------------------------------[E]
//	}
//
//	/**
//	 * 發送使用者操作日誌 (執行過程出錯)
//	 *
//	 * @param joinPoint
//	 * @param jwtUser
//	 * @param e
//	 * @throws AmqpException
//	 * @throws JsonProcessingException
//	 * @throws InterruptedException
//	 * @throws ExecutionException
//	 * @throws TimeoutException
//	 */
//	@Async
//	private void sendFailedUserOperationLog(final ProceedingJoinPoint joinPoint, final JwtUser jwtUser, final Exception e)
//			throws AmqpException, JsonProcessingException, InterruptedException, ExecutionException, TimeoutException
//	{
//		final ApiLogDto apiLogDto = prepareUserOperationLog(joinPoint, jwtUser);
//		// 決定哪些 operation 不送日誌
//		if (isOperationIgnore(apiLogDto)) return;
//
//		final String rootCauseMessage = ExceptionUtil.getExceptionRootCauseMessage(e);
//		// log.debug("\t[AOP] [發送錯誤日誌] Root Cause Message=[{}]", rootCauseMessage);
//		apiLogDto.setIsSuccessful(false);
//		apiLogDto.setOperationError(rootCauseMessage);
//		apiLogDto.setResponseStatusCode(ExceptionUtil.getHttpStatus(e).value());
//		this.amqpService.sendMsgForOperation(apiLogDto);
//	}

	/**
	 * 先取得代理方法所屬的類完整名稱，再依此取得 class name，最後去除 Ctr，剩下來的字串當作 tag
	 */
	private String getOperationTag(final ProceedingJoinPoint joinPoint)
	{
		final String controllerClassFullName = joinPoint.getSignature().getDeclaringTypeName();
		final String[] tmpArray = controllerClassFullName.split("\\.");
		final String className = tmpArray[tmpArray.length - 1];
		final String operationTag = className.replace("Ctr", "");
		return operationTag;
	}

	/**
	 * 當準備日誌內容時，須轉換入參 dco 與 duo 的資料型別，從 ControllerBaseDto 轉成 JsonNode
	 */
	private JsonNode getJsonNodeFromDto(final ProceedingJoinPoint joinPoint, final String parameterName)
	{
		ControllerBaseDto dto = null;
		try
		{
			dto = (ControllerBaseDto) AopUtil.getParameterValue(joinPoint, parameterName);
		} catch (final Exception e)
		{
			log.warn("當準備日誌內容時，轉換 API 入參 ({}) 失敗，原因：{}", parameterName, e.getMessage());
		}
		return this.objectMapper.valueToTree(dto);
	}

	/**
	 * 決定哪些 operation 不送日誌
	 */
	private Boolean isOperationIgnore(final ApiLogDto apiLogDto)
	{
		final ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
		final HttpServletRequest request = attributes.getRequest();
		// Get 類型不發送日誌
		if (request.getMethod().equals("GET")) return true;

		final List<String> operationNames = List.of("getYangValue");
		if (operationNames.contains(apiLogDto.getOperationName())) return true;

		return false;
	}
}