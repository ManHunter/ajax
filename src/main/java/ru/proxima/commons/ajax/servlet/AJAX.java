package ru.proxima.commons.ajax.servlet;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.ServiceLoader;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.proxima.commons.ajax.AJAXExecuteException;
import ru.proxima.commons.ajax.AJAXHandler;
import ru.proxima.commons.ajax.AJAXRequest;
import ru.proxima.commons.ajax.AJAXResponse;
import ru.proxima.commons.ajax.CommonAJAXHandler;
import ru.proxima.commons.json.JSONException;
import ru.proxima.commons.json.JSONObject;
/**
 * Сервлет загрузки, хранения и вызова классов-обработчиков AJAX-запросов
 * @author Шомин Владимир, ЗАО ИВЦ Инсофт
 */
@WebServlet(name="AJAX", urlPatterns="/AJAX")
public final class AJAX extends HttpServlet {

	private static HashMap<String, AJAXHandler> handlers = new HashMap<String, AJAXHandler>();
	private static final Logger logger = LoggerFactory.getLogger(AJAX.class);

	private String siteRoot;
/**
 * Инициализация данного сервлета сопровождается загрузкой всех AJAX-обработчиков
 * из доступного загрузчика классов
 * @param	sconf	не имеет значения
 */
	@Override public void init(ServletConfig sconf) {
		siteRoot = sconf.getServletContext().getRealPath("WEB-INF/").replaceFirst(".WEB-INF.?$", "");
		loadHandlers(AJAX.class.getClassLoader());
	}
/**
 * Метод загрузки AJAX-обработчиков из указанного загрузчика классов, использует {@link ServiceLoader}
 * @param	cl	Целевой загрузчик классов для исследования
 */
	public static synchronized void loadHandlers(ClassLoader cl) {
		for (CommonAJAXHandler handler : ServiceLoader.load(CommonAJAXHandler.class, cl)) {
			final String index = handler.getModule() + "." + handler.getAction();
			if (!handlers.containsKey(index)) {
				handlers.put(index, handler);
				logger.trace("Загружен AJAX-обработчик " + index);
			}
		}
	}
/**
 * Метод загрузки AJAX-обработчиков из указанного загрузчика классов, использует {@link ServiceLoader}
 * @param	action	Имя AJAX-обработчика
 * @param	module	Модуль AJAX-обработчика
 * @throws	UnsupportedOperationException	Недопустимые параметры, либо затребованный обработчик не обнаружен
 */
	public static AJAXHandler getHandler(String action, String module)
			throws UnsupportedOperationException{
		if (null == action) {
			throw new UnsupportedOperationException("Action is not defined");
		} else if (null == module) {
			module = CommonAJAXHandler.getDefaultModuleName();
		}
		AJAXHandler handler = handlers.get(module + "." + action);
		if (null == handler) {
			throw new UnsupportedOperationException("Handler " + module + "." + action + " is not loaded");
		} else {
			return handler;
		}
	}

	private void processRequest(HttpServletRequest request, HttpServletResponse response)
			throws IOException {
		request.setCharacterEncoding("UTF-8");
		PrintWriter out;
		AJAXRequest ajaxRequest = new AJAXRequest(request);
		AJAXResponse ajaxResponse = new AJAXResponse(response);
		String paramsStr = request.getParameter("params");
		JSONObject params = null;
		AJAXHandler handler = null;

		try {
			handler = getHandler(request.getParameter("action"), request.getParameter("module"));
		} catch (UnsupportedOperationException ex) {
			logger.warn("Обращение к несуществующему обработчику", ex);
			response.sendError(HttpServletResponse.SC_NOT_IMPLEMENTED);
			return;
		}

		try {
			if (null != paramsStr) {
				params = new JSONObject(paramsStr);
				Long userId = (Long)request.getSession().getAttribute("userId");
				params.putOpt("userId", userId);
			}
		} catch (JSONException ex) {
			logger.debug("Не удалось установить параметр ID пользователя", ex);
			params = new JSONObject();
		}

		try {
			handler.setParam("siteRoot", siteRoot);
			switch (handler.getHandlerType()) {
				case TEXT:
					response.setContentType(handler.getContentType());
					out = response.getWriter();
					handler.process(ajaxRequest, out);
					out.close();
					break;
				case RAW:
					handler.process(ajaxRequest, ajaxResponse);
					break;
				case JSON_INPUT:
					response.setContentType(handler.getContentType());
					out = response.getWriter();
					handler.process(params, out);
					out.close();
					break;
				case JSON_INPUT_RAW:
					handler.process(params, ajaxResponse);
					break;
				case JSON_OUTPUT:
					response.setContentType(handler.getContentType());
					out = response.getWriter();
					out.print(handler.process(ajaxRequest).toString());
					out.close();
					break;
				case JSON_IN_OUT:
					response.setContentType(handler.getContentType());
					out = response.getWriter();
					out.print(handler.process(params).toString());
					out.close();
					break;
			} // switch (handler.getHandlerType()) {
		} catch (AJAXExecuteException ex) {
			logger.error("Ошибка исполнения AJAX-запроса", ex);
			switch (handler.getHandlerType()) {
				case JSON_OUTPUT:
				case JSON_IN_OUT:
					out = response.getWriter();
					out.print(CommonAJAXHandler.getErrorJSON(ex.getLocalizedMessage()));
					out.close();
					break;
				default:
			}
		} catch (RuntimeException ex) {
			logger.error("Unhandled exception", ex);
			switch (handler.getHandlerType()) {
				case JSON_OUTPUT:
				case JSON_IN_OUT:
					out = response.getWriter();
					out.print(CommonAJAXHandler.getErrorJSON(ex.getLocalizedMessage()));
					out.close();
					break;
				default:
			}
		}
	}

	// <editor-fold defaultstate="collapsed" desc="HttpServlet methods. Click on the + sign on the left to edit the code.">
	/**
	 * Handles the HTTP <code>GET</code> method.
	 * @param request servlet request
	 * @param response servlet response
	 * @throws ServletException if a servlet-specific error occurs
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
		processRequest(request, response);
	}

	/**
	 * Handles the HTTP <code>POST</code> method.
	 * @param request servlet request
	 * @param response servlet response
	 * @throws ServletException if a servlet-specific error occurs
	 * @throws IOException if an I/O error occurs
	 */
	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response)
	throws ServletException, IOException {
		processRequest(request, response);
	}// </editor-fold>

}