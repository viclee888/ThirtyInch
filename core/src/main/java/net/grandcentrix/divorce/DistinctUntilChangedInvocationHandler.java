package net.grandcentrix.divorce;

import net.grandcentrix.divorce.util.AbstractInvocationHandler;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

final class DistinctUntilChangedInvocationHandler<V> extends AbstractInvocationHandler {

    private HashMap<String, Integer> mLatestMethodCalls = new HashMap<>();

    private final Logger mLogger;

    private final V mView;

    public DistinctUntilChangedInvocationHandler(V view) {
        mView = view;
        mLogger = Logger.getLogger(toString());
    }

    @Override
    public String toString() {
        return "DistinctUntilChangedProxy@"
                + Integer.toHexString(this.hashCode()) + "-" + mView.toString();
    }

    @Override
    protected Object handleInvocation(final Object proxy, final Method method, final Object[] args)
            throws Throwable {

        //noinspection TryWithIdenticalCatches
        try {
            // If the method is a method from Object then defer to normal invocation.
            final Class<?> declaringClass = method.getDeclaringClass();
            if (declaringClass == Object.class) {
                return method.invoke(this, args);
            }

            // always call methods with zero arguments
            if (args == null || args.length == 0) {
                return method.invoke(mView, args);
            }

            // only void methods support distinctUntilChanged
            if (!method.getReturnType().equals(Void.TYPE)) {
                return method.invoke(mView, args);
            }

            // @DistinctUntilChanged is only valid on methods of the view interface extending View
            if (!View.class.isAssignableFrom(declaringClass)) {
                return method.invoke(mView, args);
            }

            final DistinctUntilChanged ducAnnotation =
                    method.getAnnotation(DistinctUntilChanged.class);

            // check if method is correct annotated
            if (ducAnnotation == null) {
                return method.invoke(mView, args);
            }

            final String methodName = method.toGenericString();
            final int hashNow = Arrays.hashCode(args);

            if (!mLatestMethodCalls.containsKey(methodName)) {
                // first call to method
                Object result = method.invoke(mView, args);
                mLatestMethodCalls.put(methodName, hashNow);
                return result;
            }

            final Integer hashBefore = mLatestMethodCalls.get(methodName);
            if (hashBefore != hashNow) {
                // arguments changed, call the method
                Object result = method.invoke(mView, args);
                mLatestMethodCalls.put(methodName, hashNow);
                return result;
            } else {
                // don't call the method, the exact same data was already sent to the view
                if (ducAnnotation.logDropped()) {
                    mLogger.log(Level.INFO, "not calling " + method
                            + " with args " + Arrays.toString(args) + "."
                            + " Was already called with the same parameters before.");
                }
                return null;
            }

        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
            return null;
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            return null;
        }
    }
}
