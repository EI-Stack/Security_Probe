package oam.security.exception;

import oam.security.exception.base.ExceptionBase;

public class DomainAuthorizationException extends ExceptionBase
{
	private static final long	serialVersionUID	= 6404669405855575086L;

	public DomainAuthorizationException()
	{
		super("There is no domain authorization");
		this.setErrorCode(101);
	}
}
