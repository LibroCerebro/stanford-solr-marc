package org.solrmarc.testUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.Permission;
import java.util.*;

public class CommandLineUtils
{
    public static void compareUtilOutputLines(InputStream stdin, String outputLineExpected[], int numLinesToSkip, int numToCompare)
    {
        BufferedReader reader2 = null;
        try
        {
            reader2 = new BufferedReader(new InputStreamReader(stdin, "UTF-8"));
        }
        catch (UnsupportedEncodingException e1)
        {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        
        String line = null;
        int lineCnt = 0;
        try
        {
            while ((line = reader2.readLine()) != null)
            {
                if (numLinesToSkip > 0 && lineCnt < numLinesToSkip || line.length() == 0) 
                {
                    // do nothing;
                }
                else if (numToCompare > 0 && lineCnt - numLinesToSkip < numToCompare && outputLineExpected.length > lineCnt - numLinesToSkip) 
                {
                    if (line.equals("Flushing results...") || line.equals("Flushing results done") || line.startsWith("Cobertura:"))
                        continue;   // skip this line and don't even count it.  I don't know where these "Flushing Results..." lines are coming from.

                    if (! line.equals(outputLineExpected[lineCnt - numLinesToSkip]))
                        System.out.println("output line ["+ line + "]  doesn't match expected ["+ outputLineExpected[lineCnt - numLinesToSkip]+"]" );
//                    else
//                    {
//                        System.out.println("output line ["+ line + "]  matches expected ");
//                    }
                    assertEquals("output line doesn't match expected", line, outputLineExpected[lineCnt - numLinesToSkip] );
                }
                lineCnt++;
            }
        }
        catch (IOException e)
        {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
   
    public static void compareUtilOutputLine(InputStream stdin, String outputLineExpected, int numLinesToSkip)
    {
        compareUtilOutputLines(stdin, new String[]{outputLineExpected}, numLinesToSkip, 1);
    }
    
    public static void compareUtilOutput(InputStream stdin, String[] outputLinesExpected)
    {
        compareUtilOutputLines(stdin, outputLinesExpected, 0, outputLinesExpected.length);
    }
    
    private static class ExitException extends SecurityException 
    {
        private static final long serialVersionUID = -1982617086752946683L;
        public final int status;

        public ExitException(int status) 
        {
            super("There is no escape!");
            this.status = status;
        }
    }

    private static class NoExitSecurityManager extends SecurityManager 
    {
        @Override
        public void checkPermission(Permission perm) 
        {
            // allow anything.
        }

        @Override
        public void checkPermission(Permission perm, Object context) 
        {
            // allow anything.
        }

        @Override
        public void checkExit(int status) 
        {
            super.checkExit(status);
            throw new ExitException(status);
        }
    }


    public static void runCommandLineUtil(String className, String methodName, InputStream stdin, OutputStream stdout, 
                                          OutputStream stderr, String[] args, Map<String, String> addnlProps)
    {
        if (methodName == null)
        {
            JavaInvoke vmspawner = null;
            Process p;
            try
            {
                vmspawner = new JavaInvoke(className,
                                           new File("."), 
                                           addnlProps, 
                                           args,
                                           null,
                                           null, true);
                p = vmspawner.startStdinStdoutStderrInstance(className, stdin, stdout, stderr);
                p.waitFor();
            }
            catch (SecurityException e)
            {
                fail("Unable to access specified method "+methodName+" to invoke within class "+className+"");
            }
            catch (IllegalArgumentException e)
            {
                fail("Illegal arguments for specified method "+methodName+" to invoke within class "+className+"");
            }
            catch (IOException e)
            {
                fail("Error reading/writing from java process "+methodName+" to invoke within class "+className+"");
            }
            catch (InterruptedException e)
            {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        else
        {
            InputStream origIn = System.in;
            PrintStream origOut = System.out;
            PrintStream origErr = System.err;
            Class clazz;
            Method method;
            Map<String, String> backupProps = new LinkedHashMap<String, String>();
            Map<String, String> allOrigProps = new LinkedHashMap<String, String>();
            SecurityManager savedSecurityManager = System.getSecurityManager();
            System.setSecurityManager(new NoExitSecurityManager());
            checkpointProps(allOrigProps);
            addProps(addnlProps, backupProps);
            try
            {
                clazz = Class.forName(className);
                method = clazz.getMethod(methodName, String[].class);
                if (stdin != null) System.setIn(stdin);
                if (stdout != null) System.setOut(new PrintStream(stdout));
                if (stderr != null) System.setErr(new PrintStream(stderr));
                method.invoke(null, (Object)args);
            }
            catch (ExitException e)
            {
                System.setOut(origOut);
                System.setErr(origErr);
                System.out.println("class "+ className +" called System.exit("+e.status+")" );
            }
            catch (ClassNotFoundException e)
            {
                fail("Unable to find specified class "+className+" to invoke");
            }
            catch (SecurityException e)
            {
                fail("Unable to access specified method "+methodName+" to invoke within class "+className+"");
            }
            catch (NoSuchMethodException e)
            {
                fail("Unable to find specified method "+methodName+" to invoke within class "+className+"");
            }
            catch (IllegalArgumentException e)
            {
                fail("Illegal arguments for specified method "+methodName+" to invoke within class "+className+"");
            }
            catch (IllegalAccessException e)
            {
                fail("Unable to access specified method "+methodName+" to invoke within class "+className+"");
            }
            catch (InvocationTargetException e)
            {
                System.setOut(origOut);
                System.setErr(origErr);
                if (e.getTargetException() instanceof ExitException)
                {
//                    System.out.println("class "+ className +" called System.exit("+((ExitException)(e.getTargetException())).status+")" );
                }
                else
                {
                    e.getTargetException().printStackTrace();
                    fail("Specified method "+methodName+" threw an exception "+e.getTargetException().getClass().getName());
                }
            }
            finally
            {
                System.setSecurityManager(savedSecurityManager);
                removeProps(addnlProps, backupProps);
                restoreProps(allOrigProps);
                System.setIn(origIn);
                System.setOut(origOut);
                System.setErr(origErr);
            }
        }
    }

    public static void checkpointProps(Map<String, String> allOrigProps)
    {
        Properties props = System.getProperties();
        for (Object key : props.keySet())
        {
            String value = System.getProperty(key.toString());
            allOrigProps.put(key.toString(), value);
        }       
    }
    
    public static void restoreProps(Map<String, String> allOrigProps)
    {
        Properties props = System.getProperties();
        Set<String> sysPropKeys = new LinkedHashSet<String>();
        for (Object keyObj : props.keySet())
        {
            String key = keyObj.toString();
            sysPropKeys.add(key);
        }
        for (String key : sysPropKeys)
        {
            String value = System.getProperty(key);
            if (allOrigProps.containsKey(key))
            {
                String origValue = allOrigProps.get(key);
                if (!value.equals(origValue))
                    System.setProperty(key, origValue);
            }
            else
                System.clearProperty(key);

            allOrigProps.put(key.toString(), value);
        }       
    }

    public static void addProps(Map<String, String> addnlProps, Map<String, String> saveProps)
    {
        if (addnlProps != null)
        {
            for (String addnlPropsKey : addnlProps.keySet())
            {
                String addnlPropsValue = addnlProps.get(addnlPropsKey);
                String sysPropValue = System.getProperty(addnlPropsKey);
                if (sysPropValue != null) 
//                	saveProps.put(addnlPropsKey, sysPropValue);
                	saveProps.put(addnlPropsKey, addnlPropsValue);
                System.setProperty(addnlPropsKey, addnlPropsValue);
            }
        }
    }
    
    public static void removeProps(Map<String, String> addnlProps, Map<String, String> saveProps)
    {
        if (addnlProps != null)
        {
            for (String key : addnlProps.keySet())
            {
                String value = saveProps.get(key);
                if (value != null) 
                    System.setProperty(key, value);
                else
                    System.clearProperty(key);
            }
        }
    }

    public static void runCommandLineUtil(String className, String methodName, InputStream stdin, OutputStream stdout, String[] args)
    {
        runCommandLineUtil(className, methodName, stdin, stdout, null, args, null);
    }
    
    public static void runCommandLineUtil(String className, String methodName, InputStream stdin, OutputStream stdout, OutputStream stderr, String[] args)
    {
        runCommandLineUtil(className, methodName, stdin, stdout, stderr, args, null);
    }
    
    public static void runCommandLineUtil(String className, String methodName, InputStream stdin, OutputStream stdout, String[] args, Map<String,String> addnlProps)
    {
        runCommandLineUtil(className, methodName, stdin, stdout, null, args, addnlProps);
    }
    
    public static void assertArrayEquals(String message, byte[] byteArray1, byte[] byteArray2)
    {
        assertEquals(message + " lengths not equal", byteArray1.length, byteArray2.length);
        
        for (int i = 0 ; i < byteArray1.length; i++)
        {
            assertEquals(message + "byte at offset "+ i + " not equal", byteArray1[i], byteArray2[i]);
        }        
    }
                                                          
}
