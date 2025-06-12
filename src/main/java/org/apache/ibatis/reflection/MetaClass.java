/*
 *    Copyright 2009-2025 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       https://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import org.apache.ibatis.reflection.invoker.Invoker;
import org.apache.ibatis.reflection.property.PropertyTokenizer;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;

/**
 * 类的元数据，基于 Reflector 和 PropertyTokenizer ，提供对指定类的各种骚操作。
 *
 * @author Clinton Begin
 */
public class MetaClass {

  private final ReflectorFactory reflectorFactory;
  private final Reflector reflector;

  private MetaClass(Type type, ReflectorFactory reflectorFactory) {
    this.reflectorFactory = reflectorFactory;
    this.reflector = reflectorFactory.findForClass(type);
  }

  /**
   * 创建指定类的 MetaClass 对象
   *
   * @param type
   * @param reflectorFactory
   * @return
   */
  public static MetaClass forClass(Type type, ReflectorFactory reflectorFactory) {
    return new MetaClass(type, reflectorFactory);
  }

  /**
   * 创建类的指定属性的类的 MetaClass 对象
   *
   * @param name
   * @return
   */
  public MetaClass metaClassForProperty(String name) {
    // 获得属性的类
    Class<?> propType = reflector.getGetterType(name);
    return MetaClass.forClass(propType, reflectorFactory);
  }

  /**
   * 根据表达式，获得属性
   *
   * @param name
   * @return
   */
  public String findProperty(String name) {
    // <3> 构建属性
    StringBuilder prop = buildProperty(name, new StringBuilder());
    return prop.length() > 0 ? prop.toString() : null;
  }

  public String findProperty(String name, boolean useCamelCaseMapping) {
    if (useCamelCaseMapping) {
      // // <1> 下划线转驼峰
      name = name.replace("_", "");
    }
    // <2> 获得属性
    return findProperty(name);
  }

  public String[] getGetterNames() {
    return reflector.getGetablePropertyNames();
  }

  public String[] getSetterNames() {
    return reflector.getSetablePropertyNames();
  }

  public Class<?> getSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.getSetterType(prop.getChildren());
    }
    return reflector.getSetterType(prop.getName());
  }

  public Entry<Type, Class<?>> getGenericSetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGenericSetterType(prop.getChildren());
    }
    return reflector.getGenericSetterType(prop.getName());
  }

  /**
   * 获得指定属性的 getting 方法的返回值的类型
   *
   * @param name
   * @return
   */
  public Class<?> getGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGetterType(prop.getChildren());
    }
    return getGetterType(prop).getValue();
  }

  public Entry<Type, Class<?>> getGenericGetterType(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (prop.hasNext()) {
      MetaClass metaProp = metaClassForProperty(prop);
      return metaProp.getGenericGetterType(prop.getChildren());
    }
    return getGetterType(prop);
  }

  private MetaClass metaClassForProperty(PropertyTokenizer prop) {
    Class<?> propType = getGetterType(prop).getValue();
    return MetaClass.forClass(propType, reflectorFactory);
  }

  private Entry<Type, Class<?>> getGetterType(PropertyTokenizer prop) {
    // Resolve the type inside a Collection Object
    // https://github.com/mybatis/old-google-code-issues/issues/506
    Entry<Type, Class<?>> pair = reflector.getGenericGetterType(prop.getName());
    if (prop.getIndex() != null && Collection.class.isAssignableFrom(pair.getValue())) {
      Type returnType = pair.getKey();
      if (returnType instanceof ParameterizedType) {
        Type[] actualTypeArguments = ((ParameterizedType) returnType).getActualTypeArguments();
        if (actualTypeArguments != null && actualTypeArguments.length == 1) {
          returnType = actualTypeArguments[0];
          if (returnType instanceof Class) {
            return Map.entry(returnType, (Class<?>) returnType);
          } else if (returnType instanceof ParameterizedType) {
            return Map.entry(returnType, (Class<?>) ((ParameterizedType) returnType).getRawType());
          }
        }
      }
    }
    return pair;
  }

  public boolean hasSetter(String name) {
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (!prop.hasNext()) {
      return reflector.hasSetter(prop.getName());
    }
    if (reflector.hasSetter(prop.getName())) {
      MetaClass metaProp = metaClassForProperty(prop.getName());
      return metaProp.hasSetter(prop.getChildren());
    }
    return false;
  }

  /**
   * 判断指定属性是否有 getting 方法
   *
   * @param name
   * @return
   */
  public boolean hasGetter(String name) {
    // 创建 PropertyTokenizer 对象，对 name 进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    if (!prop.hasNext()) {
      return reflector.hasGetter(prop.getName());
    }
    // 判断是否有该属性的 getting 方法
    if (reflector.hasGetter(prop.getName())) {
      // <1> 创建 MetaClass 对象
      MetaClass metaProp = metaClassForProperty(prop);
      // 递归判断子表达式 children ，是否有 getting 方法
      return metaProp.hasGetter(prop.getChildren());
    }
    return false;
  }

  public Invoker getGetInvoker(String name) {
    return reflector.getGetInvoker(name);
  }

  public Invoker getSetInvoker(String name) {
    return reflector.getSetInvoker(name);
  }

  /**
   * 创建 PropertyTokenizer 对象，对 `name` 进行**分词**。当有子表达式，继续递归调用 `
   * #buildProperty(String name, StringBuilder builder)` 方法，并将结果添加到 `builder` 中；否则，结束，直接添加到 `builder` 中。
   * * 在两个 `<4>` 处，解决“下划线转驼峰”的关键是，通过 `Reflector.caseInsensitivePropertyMap` 属性，忽略大小写。代码如下：
   *
   * @param name
   * @param builder
   * @return
   */
  private StringBuilder buildProperty(String name, StringBuilder builder) {
    // 创建 PropertyTokenizer 对象，对 name 进行分词
    PropertyTokenizer prop = new PropertyTokenizer(name);
    // 有子表达式
    if (prop.hasNext()) {
      // <4> 获得属性名，并添加到 builder 中
      String propertyName = reflector.findPropertyName(prop.getName());
      if (propertyName != null) {
        // 拼接属性到 builder 中
        builder.append(propertyName);
        builder.append(".");
        // 创建 MetaClass 对象
        MetaClass metaProp = metaClassForProperty(propertyName);
        // 递归解析子表达式 children ，并将结果添加到 builder 中
        metaProp.buildProperty(prop.getChildren(), builder);
      }
    } else {
      // 无子表达式
      // <4> 获得属性名，并添加到 builder 中
      String propertyName = reflector.findPropertyName(name);
      if (propertyName != null) {
        builder.append(propertyName);
      }
    }
    return builder;
  }

  public boolean hasDefaultConstructor() {
    return reflector.hasDefaultConstructor();
  }

}
