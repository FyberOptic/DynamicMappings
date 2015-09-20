package net.fybertech.dynamicmappings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface MappingsClass {
	boolean clientSide() default false;
	boolean serverSide() default false;
}
