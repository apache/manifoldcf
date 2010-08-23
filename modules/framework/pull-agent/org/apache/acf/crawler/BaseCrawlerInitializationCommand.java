package org.apache.acf.crawler;

import org.apache.acf.core.InitializationCommand;
import org.apache.acf.core.interfaces.IThreadContext;
import org.apache.acf.core.interfaces.LCFException;
import org.apache.acf.core.interfaces.ThreadContextFactory;
import org.apache.acf.crawler.system.LCF;

/**
 * @author Jettro Coenradie
 */
public abstract class BaseCrawlerInitializationCommand implements InitializationCommand
{
  public void execute() throws LCFException
  {
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws LCFException;

}
