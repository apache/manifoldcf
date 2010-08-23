package org.apache.lcf.crawler;

import org.apache.lcf.core.InitializationCommand;
import org.apache.lcf.core.interfaces.IThreadContext;
import org.apache.lcf.core.interfaces.LCFException;
import org.apache.lcf.core.interfaces.ThreadContextFactory;
import org.apache.lcf.crawler.system.LCF;

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
