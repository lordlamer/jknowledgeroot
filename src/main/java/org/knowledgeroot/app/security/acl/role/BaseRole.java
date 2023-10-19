package org.knowledgeroot.app.security.acl.role;

public class BaseRole implements RoleInterface {
	private String roleId;

	public BaseRole() {

	}

	public BaseRole(String roleId) {
		this.roleId = roleId;
	}

	@Override
	public String getRoleId() {
		return this.roleId;
	}

	public void setRoleId(String roleId) {
		this.roleId = roleId;
	}
}
