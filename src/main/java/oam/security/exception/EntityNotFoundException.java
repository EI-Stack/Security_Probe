package oam.security.exception;

import oam.security.exception.base.ExceptionBase;

/**
 * @author Holisun Wu
 */
public class EntityNotFoundException extends ExceptionBase
{
	private static final long serialVersionUID = 1L;

	public EntityNotFoundException()
	{
		super("There is no entity found.");
		this.setErrorCode(404);
	}

	public EntityNotFoundException(final Long id)
	{
		super("There is no entity (id=" + id + ") found.");
		this.setErrorCode(404);
	}

	public EntityNotFoundException(final String entityClassName, final Long id)
	{
		super("There is no entity " + ExceptionBase.toSimple(entityClassName) + " (id=" + id + ") found.");
		this.setErrorCode(404);
	}

	public EntityNotFoundException(final String message)
	{
		super(message);
		this.setErrorCode(404);
	}
}
