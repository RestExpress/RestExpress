package com.strategicgains.restexpress.util;

import com.strategicgains.restexpress.Request;
import com.strategicgains.restexpress.Response;
import com.strategicgains.restexpress.exception.BadRequestException;
import com.strategicgains.restexpress.serialization.SerializationProcessor;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.ws.rs.PathParam;
import javax.ws.rs.QueryParam;


/**
* Restful Service parameter assembler, support jsr311 annotation
*
* @author BC
* @version $Revision$
*/
public class ParameterAssembler {
   //~ Methods ========================================================================================================

   private static Object applyParamAnnotation(Request request, Annotation[] annotations, Class<?> objClass) {
       Object ret = null;

       PathParam pathParam = AnnotationUtils.getAnnotation(annotations, PathParam.class);
       QueryParam queryParam = AnnotationUtils.getAnnotation(annotations, QueryParam.class);

       String inputName = null;

       if (null != pathParam) {
           inputName = pathParam.value();
       } else if (null != queryParam) {
           inputName = queryParam.value();
       }

       if ((null != pathParam) || (null != queryParam)) {
           if ((Date.class.equals(objClass)) || (Locale.class.equals(objClass))) {
        	   ret = readWithProcessor(request.getSerializationProcessor(), request.getHeader(inputName), objClass);
           } else {
               ret = readPrimitive(request.getHeader(inputName), objClass);
           }
       } else {
           // Complex Data type of parameters, annotation will be
           // @Context, @CookieParam, HeaderParam
       }

       return ret;
   }

   public static List<Object> assembleParameters(Method action, Request request, Response response) {
       // TODO, @Default.value(""), @Context annotation
       List<Object> args = new ArrayList<Object>();
       Class<?>[] paraTypes = action.getParameterTypes();

       if ((paraTypes.length == 2) && paraTypes[0].equals(Request.class) && paraTypes[1].equals(Response.class)) {
           args.add(request);
           args.add(response);

           return args;
       }

       Method annotatedMethod = AnnotationUtils.getAnnotatedMethod(action);
       Annotation[][] paraAnnotations = annotatedMethod.getParameterAnnotations();

       for (int i = 0; i < paraAnnotations.length; i++) {
           Object ret = null;
           Class<?> objClass = paraTypes[i];

           if (paraAnnotations[i].length != 0) {
               ret = applyParamAnnotation(request, paraAnnotations[i], objClass);
           } else {
               // No annotated parameters 
               ret = objClass.cast(request.getBodyAs(objClass));

               //TODO, validation ret = DtoAssembler.mapAndValidate(request, null, clazz);
           }

           args.add(ret);
       }

       return args;
   }

   public static <T> Object readPrimitive(String value, Class<T> type) {
       Object ret = value;

       try {
           if (Integer.TYPE.equals(type) || Integer.class.equals(type)) {
               ret = Integer.valueOf(value);
           }

           if (Byte.TYPE.equals(type) || Byte.class.equals(type)) {
               ret = Byte.valueOf(value);
           }

           if (Short.TYPE.equals(type) || Short.class.equals(type)) {
               ret = Short.valueOf(value);
           }

           if (Long.TYPE.equals(type) || Long.class.equals(type)) {
               ret = Long.valueOf(value);
           }

           if (Float.TYPE.equals(type) || Float.class.equals(type)) {
               ret = Float.valueOf(value);
           }

           if (Double.TYPE.equals(type) || Double.class.equals(type)) {
               ret = Double.valueOf(value);
           }

           if (Boolean.TYPE.equals(type) || Boolean.class.equals(type)) {
               ret = Boolean.valueOf(value);
           }

           if (Character.TYPE.equals(type) || Character.class.equals(type)) {
               ret = value.charAt(0);
           }
       } catch (NumberFormatException nfe) {
           // For path, query & matrix parameters this is 404,
           // for others 400...
           throw new BadRequestException(nfe);
       }

       return ret;
   }

   public static <T> Object readWithProcessor(SerializationProcessor processor, String value, Class<T> type) {
       Object ret = value;

       if (Date.class.equals(type)) {
           ret = processor.deserialize("\"" + value + "\"", type);
       } else if (Locale.class.equals(type)) {
           ret = processor.deserialize(value, type);
       }

       return ret;
   }
}