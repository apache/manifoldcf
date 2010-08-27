package org.apache.acf.crawler;

import org.apache.acf.core.InitializationCommand;
import org.apache.acf.core.interfaces.IThreadContext;
import org.apache.acf.core.interfaces.ACFException;
import org.apache.acf.core.interfaces.ThreadContextFactory;
import org.apache.acf.crawler.system.ACF;

/**
 * @author Jettro Coenradie
 */
public abstract class BaseCrawlerInitializationCommand implements InitializationCommand
{
  public void execute() throws ACFException
  {
    ACF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws ACFException;

}
