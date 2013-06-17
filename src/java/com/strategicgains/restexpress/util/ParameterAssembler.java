package com.strategicgains.restexpress.util;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import com.strategicgains.restexpress.Request;
import com.strategicgains.restexpress.Response;
import com.strategicgains.restexpress.exception.BadRequestException;
import com.strategicgains.restexpress.serialization.SerializationProcessor;


/**
* Restful Service parameter assembler, support jsr311 annotation
*
* @author BC
* @version $Revision$
*/
public class ParameterAssembler {
	private static final String HALF_QUOTE="\"";
	
   //~ Methods ========================================================================================================

   private static Object applyParamAnnotation(Request request, Annotation[] annotations, Class<?> objClass) {
       Object ret = null;
       
       String annotationValue = getAnnotationvalue(annotations);

       if (null != annotationValue) {
           if ((Date.class.equals(objClass)) || (Locale.class.equals(objClass))) {
        	   ret = readWithProcessor(request.getSerializationProcessor(), request.getHeader(annotationValue), objClass);
           } else {
               ret = readPrimitive(request.getHeader(annotationValue), objClass);
           }
       } else {
           // Complex Data type of parameters, annotation will be @Context
       }

       return ret;
   }

	private static String getAnnotationvalue(Annotation[] annotations) {
		String annValue = null;
		for (Annotation ann : annotations) {
			annValue = AnnotationUtils.getAnnotationValue(ann);
			if (null != annValue) {
				break;
			}
		}
		return annValue;
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
       
       Annotation[][] paraAnnotations = action.getParameterAnnotations();

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
           ret = processor.deserialize(HALF_QUOTE + value + HALF_QUOTE, type);
       } else if (Locale.class.equals(type)) {
           ret = processor.deserialize(value, type);
       }

       return ret;
   }
}