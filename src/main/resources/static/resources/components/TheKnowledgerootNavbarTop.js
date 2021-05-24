export const TheKnowledgerootNavbarTop = {
    data() {
        return {
            name: "FooBar"
        }
    },
    template: `
        <nav class="navbar fixed-top navbar-dark bg-dark navbar-expand-lg" style="border-bottom: 3px solid #F88529;">
            <a class="navbar-brand" href="#" data-toggle="dropdown"><i class="fas fa-bars"></i></a>
            <div class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink">
                <a class="dropdown-item" href="#"><i class="fas fa-rocket"></i>&nbsp;Knowledgeroot</a>
                <a class="dropdown-item" href="#"><i class="fas fa-book"></i>&nbsp;Docs</a>
                <a class="dropdown-item" href="#"><i class="fas fa-puzzle-piece"></i>&nbsp;API</a>
            </div>
            <a class="navbar-brand" href="#"><i class="fas fa-hashtag" aria-hidden="true"></i> Knowledgeroot</a>
            <button class="navbar-toggler" type="button" data-toggle="collapse" data-target="#navbarText" aria-controls="navbarText" aria-expanded="false" aria-label="Toggle navigation">
                <span class="navbar-toggler-icon"></span>
            </button>
            <div class="collapse navbar-collapse" id="navbarText">
                <ul class="navbar-nav mr-auto">
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                            <i class="fas fa-plus icon-white"></i>
                        </a>
                        <div class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink">
                            <a class="dropdown-item" href="#"><i class="fas fa-file"></i>&nbsp;Page</a>
                            <a class="dropdown-item" href="#"><i class="fas fa-file-alt"></i>&nbsp;Content</a>
                            <a class="dropdown-item" href="#"><i class="fas fa-paperclip"></i>&nbsp;File</a>
                        </div>
                    </li>
                </ul>
                <ul class="navbar-nav navbar-right">
                    <li class="nav-item dropdown">
                        <input class="form-control mr-4" type="search" placeholder="Search" aria-label="Search">
                    </li>
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                            <i class="fas fa-question-circle icon-white"></i>
                        </a>
                        <div class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink">
                            <a class="dropdown-item" href="#"><i class="fas fa-book"></i>&nbsp;Help</a>
                            <a class="dropdown-item" href="#"><i class="fas fa-star"></i>&nbsp;About</a>
                        </div>
                    </li>
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                            <i class="fas fa-cogs icon-white"></i>
                        </a>
                        <div class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink">
                            <a class="dropdown-item" href="#"><i class="fas fa-file"></i>&nbsp;Users</a>
                            <a class="dropdown-item" href="#"><i class="fas fa-file-alt"></i>&nbsp;Groups</a>
                            <a class="dropdown-item" href="#"><i class="fas fa-paperclip"></i>&nbsp;Permissions</a>
                        </div>
                    </li>
                    <li class="nav-item dropdown">
                        <a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                            <i class="fas fa-user icon-white"></i>
                        </a>
                        <div class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink">
                            <a class="dropdown-item" href="#"><i class="fas fa-sign-in-alt"></i>&nbsp;Sign in</a>
                        </div>
                    </li>
                </ul>
            </div>
        </nav>`
}

export default TheKnowledgerootNavbarTop;