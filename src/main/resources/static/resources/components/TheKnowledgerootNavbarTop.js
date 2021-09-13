export const TheKnowledgerootNavbarTop = {
    data() {
        return {
            name: "FooBar"
        }
    },
    template: `
<nav class="navbar navbar-expand-lg fixed-top navbar-dark bg-dark" style="border-bottom: 3px solid #F88529;">
	<ul class="container-fluid">
		<ul class="navbar-nav me-auto mb-2 mb-lg-0">
			<li class="nav-item dropdown">
				<a class="navbar-brand" href="#" id="navbarDropdown" role="button" data-bs-toggle="dropdown" aria-expanded="false">
					<i class="fas fa-bars"/>
				</a>
				<ul class="dropdown-menu" aria-labelledby="navbarDropdown">
					<li>
						<a class="dropdown-item" href="#">
							<i class="fas fa-rocket"/>&nbsp;Knowledgeroot</a>
					</li>
					<li>
						<a class="dropdown-item" href="#">
							<i class="fas fa-book"/>&nbsp;Docs</a>
					</li>
					<li>
						<a class="dropdown-item" href="#">
							<i class="fas fa-puzzle-piece"/>&nbsp;API</a>
					</li>
				</ul>
			</li>
		</ul>

		<a class="navbar-brand" href="#"><i class="fas fa-hashtag" aria-hidden="true"/> Knowledgeroot</a>
		<button class="navbar-toggler" type="button" data-bs-toggle="collapse" data-bs-target="#navbarSupportedContent" aria-controls="navbarSupportedContent" aria-expanded="false" aria-label="Toggle navigation">
			<span class="navbar-toggler-icon"/>
		</button>
		<div class="collapse navbar-collapse" id="navbarSupportedContent">
			<ul class="navbar-nav me-auto mb-2 mb-lg-0">
				<!-- create new item -->
				<li class="nav-item dropdown">
					<a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink" role="button" data-bs-toggle="dropdown" aria-expanded="false">
						<i class="fas fa-plus icon-white"/>
					</a>
					<ul class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink">
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-file"/>&nbsp;Page</a>
						</li>
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-file-alt"/>&nbsp;Content</a>
						</li>
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-paperclip"/>&nbsp;File</a>
						</li>
					</ul>
				</li>
				<!-- user -->
				<li class="nav-item dropdown">
					<a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink2" role="button" data-bs-toggle="dropdown" aria-expanded="false">
						<i class="fas fa-user icon-white"/>
					</a>
					<ul class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink2">
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-sign-in-alt"/>&nbsp;Sign in</a>
						</li>
					</ul>
				</li>
				<!-- help / info -->
				<li class="nav-item dropdown">
					<a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink3" role="button" data-bs-toggle="dropdown" aria-expanded="false">
						<i class="fas fa-question-circle icon-white"/>
					</a>
					<ul class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink3">
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-book"/>&nbsp;Help</a>
						</li>
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-star"/>&nbsp;About</a>
						</li>
					</ul>
				</li>
				<!-- settings -->
				<li class="nav-item dropdown">
					<a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink4" role="button" data-bs-toggle="dropdown" aria-expanded="false">
						<i class="fas fa-cogs icon-white"/>
					</a>
					<ul class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink4">
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-file"/>&nbsp;Users</a>
						</li>
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-file-alt"/>&nbsp;Groups</a>
						</li>
						<li>
							<a class="dropdown-item" href="#">
								<i class="fas fa-paperclip"/>&nbsp;Permissions</a>
						</li>
					</ul>
				</li>
			</ul>
			<form class="d-flex">
				<input class="form-control me-2" type="search" placeholder="Search" aria-label="Search">
				<button class="btn btn-outline-success" type="submit">Search</button>
			</form>
		</div>
	</ul>
</nav>
`
}

export default TheKnowledgerootNavbarTop;