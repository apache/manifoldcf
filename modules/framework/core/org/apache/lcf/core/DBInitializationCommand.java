package org.apache.lcf.core;

import org.apache.lcf.core.interfaces.IThreadContext;
import org.apache.lcf.core.interfaces.LCFException;
import org.apache.lcf.core.interfaces.ThreadContextFactory;
import org.apache.lcf.core.system.LCF;

/**
 * Parent class for all database initialization related commands. This class provides methods to
 * obtain username and password for the database. 
 *
 * @author Jettro Coenradie
 */
public abstract class DBInitializationCommand implements InitializationCommand
{
  private final String userName;
  private final String password;

  /**
   * The userName and password for the database on which the command needs to be performed
   *
   * @param userName String containing the mandatory database username
   * @param password String containing the mandatory database password
   */
  public DBInitializationCommand(String userName, String password)
  {
    this.userName = userName;
    this.password = password;
  }

  public void execute() throws LCFException
  {
    LCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws LCFException;

  protected String getPassword()
  {
    return password;
  }

  protected String getUserName()
  {
    return userName;
  }

}
