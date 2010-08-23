package org.apache.acf.authorities;

import org.apache.acf.authorities.interfaces.AuthorityConnectorManagerFactory;
import org.apache.acf.authorities.interfaces.IAuthorityConnectorManager;
import org.apache.acf.authorities.system.LCF;
import org.apache.acf.core.InitializationCommand;
import org.apache.acf.core.interfaces.IThreadContext;
import org.apache.acf.core.interfaces.LCFException;
import org.apache.acf.core.interfaces.ThreadContextFactory;

/**
 * @author Jettro Coenradie
 */
public abstract class BaseAuthoritiesInitializationCommand implements InitializationCommand
{
  public void execute() throws LCFException
  {
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    IAuthorityConnectorManager mgr = AuthorityConnectorManagerFactory.make(tc);

    doExecute(mgr);
  }

  protected abstract void doExecute(IAuthorityConnectorManager mgr) throws LCFException;
}
