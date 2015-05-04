/* Copyright (c) 2008, Nathan Sweet
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following
 * conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
 * - Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided with the distribution.
 * - Neither the name of Esoteric Software nor the names of its contributors may be used to endorse or promote products derived
 * from this software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
 * BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT
 * SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE. */

package com.esotericsoftware.kryo.serializers;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.GenericArrayType;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.lang.reflect.WildcardType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Generics;
import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.serializers.FieldSerializer.CachedField;

/**
 * A few utility methods for using generic type parameters, mostly by FieldSerializer
 * 
 * @author Roman Levenstein <romixlev@gmail.com>
 */
final class FieldSerializerGenericsUtil {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(FieldSerializerGenericsUtil.class);
	
	private Kryo kryo;
	private FieldSerializer serializer;

	public FieldSerializerGenericsUtil (FieldSerializer serializer) {
		this.serializer = serializer;
		this.kryo = serializer.getKryo();
	}

	/*** Create a mapping from type variable names (which are declared as type parameters of a generic class) to the concrete classes
	 * used for type instantiation.
	 * 
	 * @param clazz class with generic type arguments
	 * @param generics concrete types used to instantiate the class
	 * @return new scope for type parameters */
	Generics buildGenericsScope (Class clazz, Class[] generics) {
		final String methodName = "buildGenericsScope : ";
		
		Class typ = clazz;
		TypeVariable[] typeParams = null;

		while (typ != null) {
			if (typ == this.serializer.type)
				typeParams = this.serializer.typeParameters;
			else
				typeParams = typ.getTypeParameters();
			if (typeParams == null || typeParams.length == 0) {
				if (typ == this.serializer.type)
					typ = this.serializer.componentType;
				else
					typ = typ.getComponentType();
			} else
				break;
		}

		if (typeParams != null && typeParams.length > 0) {
			Generics genScope;
			LOGGER.trace("{} Class {} has generic type parameters", methodName, clazz.getName());
			int typeVarNum = 0;
			Map<String, Class> typeVar2concreteClass;
			typeVar2concreteClass = new HashMap<String, Class>();
			for (TypeVariable typeVar : typeParams) {
				String typeVarName = typeVar.getName();
				LOGGER.trace("{} Type parameter variable: name={}  type bounds={}", methodName, typeVarName, Arrays.toString(typeVar.getBounds()));

				final Class<?> concreteClass = getTypeVarConcreteClass(generics, typeVarNum, typeVarName);
				if (concreteClass != null) {
					typeVar2concreteClass.put(typeVarName, concreteClass);
					LOGGER.trace("{} Concrete type used for {} is: {}", methodName, typeVarName, concreteClass.getName());
				}

				typeVarNum++;
			}
			genScope = new Generics(typeVar2concreteClass);
			return genScope;
		} else
			return null;
	}

	private Class<?> getTypeVarConcreteClass (Class[] generics, int typeVarNum, String typeVarName) {
		final String methodName = "getTypeVarConcreteClass : ";
		
		if (generics != null && generics.length > typeVarNum) {
			// If passed concrete classes are known explicitly, use this information
			return generics[typeVarNum];
		} else {
			// Otherwise try to derive the information from the current GenericScope
			LOGGER.trace("{} Trying to use kryo.getGenericScope", methodName);
			Generics scope = kryo.getGenericsScope();
			if (scope != null) {
				return scope.getConcreteClass(typeVarName);
			}
		}
		return null;
	}

	Class[] computeFieldGenerics (Type fieldGenericType, Field field, Class[] fieldClass) {
		final String methodName = "computeFieldGenerics : ";
		
		Class[] fieldGenerics = null;
		if (fieldGenericType != null) {
			if (fieldGenericType instanceof TypeVariable && serializer.getGenericsScope() != null) {
				TypeVariable typeVar = (TypeVariable)fieldGenericType;
				// Obtain information about a concrete type of a given variable from the environment
				Class concreteClass = serializer.getGenericsScope().getConcreteClass(typeVar.getName());
				if (concreteClass != null) {
					fieldClass[0] = concreteClass;
					fieldGenerics = new Class[] {fieldClass[0]};
					LOGGER.trace("{} Determined concrete class of '{}' to be {}", methodName, field.getName(), fieldClass[0].getName());
				}
			} else if (fieldGenericType instanceof ParameterizedType) {
				ParameterizedType parameterizedType = (ParameterizedType)fieldGenericType;
				// Get actual type arguments of the current field's type
				Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
				// if(actualTypeArguments != null && generics != null) {
				if (actualTypeArguments != null) {
					fieldGenerics = new Class[actualTypeArguments.length];
					for (int i = 0; i < actualTypeArguments.length; ++i) {
						Type t = actualTypeArguments[i];
						if (t instanceof Class)
							fieldGenerics[i] = (Class)t;
						else if (t instanceof ParameterizedType)
							fieldGenerics[i] = (Class)((ParameterizedType)t).getRawType();
						else if (t instanceof TypeVariable && serializer.getGenericsScope() != null)
							fieldGenerics[i] = serializer.getGenericsScope().getConcreteClass(((TypeVariable)t).getName());
						else if (t instanceof WildcardType)
							fieldGenerics[i] = Object.class;
						else if (t instanceof GenericArrayType) {
							Type componentType = ((GenericArrayType)t).getGenericComponentType();
							if (componentType instanceof Class)
								fieldGenerics[i] = Array.newInstance((Class)componentType, 0).getClass();
							else if (componentType instanceof TypeVariable) {
								Generics scope = serializer.getGenericsScope();
								if (scope != null) {
									Class clazz = scope.getConcreteClass(((TypeVariable)componentType).getName());
									if (clazz != null) {
										fieldGenerics[i] = Array.newInstance(clazz, 0).getClass();
									}
								}
							}
						} else
							fieldGenerics[i] = null;
					}
					if (fieldGenerics != null) {
						LOGGER.trace("{} Determined concrete class of parametrized '{}' to be {} where type parameters are {}", methodName, field.getName(), fieldGenericType, Arrays.toString(fieldGenerics));
					}
				}
			} else if (fieldGenericType instanceof GenericArrayType) {
				// TODO: store generics for arrays as well?
				GenericArrayType arrayType = (GenericArrayType)fieldGenericType;
				Type genericComponentType = arrayType.getGenericComponentType();
				Class[] tmpFieldClass = new Class[] {fieldClass[0]};
				fieldGenerics = computeFieldGenerics(genericComponentType, field, tmpFieldClass);
				// Kryo.getGenerics(fieldGenericType);
				if (fieldGenerics != null) {
					LOGGER.trace("{} Determined concrete class of a generic array '{}' to be {} where type parameters are {}", methodName, field.getName(), fieldGenericType, Arrays.toString(fieldGenerics));
				} else {
					LOGGER.trace("{} Determined concrete class of '{}' to be {}", methodName, field.getName(), fieldGenericType);
				}
			}
		}

		return fieldGenerics;
	}
	
	/** Special processing for fiels of generic types */
	CachedField newCachedFieldOfGenericType (Field field, int accessIndex, Class[] fieldClass, Type fieldGenericType) {
		final String methodName = "newCachedFieldOfGenericType : ";
		
		Class[] fieldGenerics;
		CachedField cachedField;
		// This is a field with generic type parameters
		LOGGER.trace("{} Field '{}' of type {} of generic type {}", methodName, field.getName(), fieldClass[0], fieldGenericType);

		if (fieldGenericType != null){
			LOGGER.trace("{} Field generic type is of class {}", methodName, fieldGenericType.getClass().getName());
		}

		// Get set of provided type parameters

		// Get list of field specific concrete classes passed as generic parameters
		Class[] cachedFieldGenerics = FieldSerializerGenericsUtil.getGenerics(fieldGenericType, kryo);

		// Build a generics scope for this field
		Generics scope = buildGenericsScope(fieldClass[0], cachedFieldGenerics);

		// Is it a field of a generic parameter type, i.e. "T field"?
		if (fieldClass[0] == Object.class && fieldGenericType instanceof TypeVariable && serializer.getGenericsScope() != null) {
			TypeVariable typeVar = (TypeVariable)fieldGenericType;
			// Obtain information about a concrete type of a given variable from the environment
			Class concreteClass = serializer.getGenericsScope().getConcreteClass(typeVar.getName());
			if (concreteClass != null) {
				scope = new Generics();
				scope.add(typeVar.getName(), concreteClass);
			}
		}

		LOGGER.trace("{} Generics scope of field '{}' of class {} is {}", methodName, field.getName(), fieldGenericType, scope);

		fieldGenerics = computeFieldGenerics(fieldGenericType, field, fieldClass);
		cachedField = serializer.newMatchingCachedField(field, accessIndex, fieldClass[0], fieldGenericType, fieldGenerics);

		if (fieldGenerics != null && cachedField instanceof ObjectField) {
			if (fieldGenerics.length > 0 && fieldGenerics[0] != null) {
				// If any information about concrete types for generic arguments of current field's type
				// was deriver, remember it.
				((ObjectField)cachedField).generics = fieldGenerics;
				LOGGER.trace("{} Field generics: {}", methodName, Arrays.toString(fieldGenerics));
			}
		}
		return cachedField;
	}
	
	/** Returns the first level of classes or interfaces for a generic type.
	 * @return null if the specified type is not generic or its generic types are not classes. */
	public static Class[] getGenerics (Type genericType, Kryo kryo) {
		final String methodName = "getGenerics : ";
		
		if (genericType instanceof GenericArrayType) {
			Type componentType = ((GenericArrayType)genericType).getGenericComponentType();
			if (componentType instanceof Class)
				return new Class[] {(Class)componentType};
			else
				return getGenerics(componentType, kryo);
		}
		if (!(genericType instanceof ParameterizedType)) return null;
		LOGGER.trace("{} Processing generic type {}", methodName, genericType);
		Type[] actualTypes = ((ParameterizedType)genericType).getActualTypeArguments();
		Class[] generics = new Class[actualTypes.length];
		int count = 0;
		for (int i = 0, n = actualTypes.length; i < n; i++) {
			Type actualType = actualTypes[i];
			LOGGER.trace("{} Processing actual type {} ({})", methodName, actualType, actualType.getClass().getName());
			generics[i] = Object.class;
			if (actualType instanceof Class)
				generics[i] = (Class)actualType;
			else if (actualType instanceof ParameterizedType)
				generics[i] = (Class)((ParameterizedType)actualType).getRawType();
			else if (actualType instanceof TypeVariable) {
				Generics scope = kryo.getGenericsScope();
				if (scope != null) {
					Class clazz = scope.getConcreteClass(((TypeVariable)actualType).getName());
					if (clazz != null) {
						generics[i] = clazz;
					} else
						continue;
				} else
					continue;
			} else if (actualType instanceof GenericArrayType) {
				Type componentType = ((GenericArrayType)actualType).getGenericComponentType();
				if (componentType instanceof Class)
					generics[i] = Array.newInstance((Class)componentType, 0).getClass();
				else if (componentType instanceof TypeVariable) {
					Generics scope = kryo.getGenericsScope();
					if (scope != null) {
						Class clazz = scope.getConcreteClass(((TypeVariable)componentType).getName());
						if (clazz != null) {
							generics[i] = Array.newInstance(clazz, 0).getClass();
						}
					}
				} else {
					Class[] componentGenerics = getGenerics(componentType, kryo);
					if (componentGenerics != null) generics[i] = componentGenerics[0];
				}
			} else
				continue;
			count++;
		}
		if (count == 0) return null;
		return generics;
	}
}
