package org.apache.manifoldcf.core;

import org.apache.manifoldcf.core.interfaces.IThreadContext;
import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.interfaces.ThreadContextFactory;
import org.apache.manifoldcf.core.system.ManifoldCF;

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

  public void execute() throws ManifoldCFException
  {
    ManifoldCF.initializeEnvironment();
    IThreadContext tc = ThreadContextFactory.make();
    doExecute(tc);
  }

  protected abstract void doExecute(IThreadContext tc) throws ManifoldCFException;

  protected String getPassword()
  {
    return password;
  }

  protected String getUserName()
  {
    return userName;
  }

}
