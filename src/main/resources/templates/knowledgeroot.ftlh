<!DOCTYPE html>
<html lang="en">
<head>

    <meta charset="utf-8">
    <title>Knowledgeroot</title>
    <meta name="description" content="Knowledgeroot Knowledgebase">
    <meta name="author" content="Knowledgeroot Team">

    <!-- htmx -->
    <script src="https://unpkg.com/htmx.org@1.7.0"></script>

    <!-- jQuery -->
    <!--
    <script src="webjars/jquery/jquery.min.js"></script>
    -->

    <!-- Bootstrap -->
    <link rel="stylesheet" href="webjars/bootstrap/css/bootstrap.min.css">
    <script src="webjars/bootstrap/js/bootstrap.bundle.min.js"></script>

    <!-- Bootstrap Icons -->
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.7.2/font/bootstrap-icons.css">

    <!-- vuejs -->
    <!--
    <script src="https://unpkg.com/vue@next"></script>
    -->

    <!-- axios -->
    <!--
    <script src="https://cdn.jsdelivr.net/npm/axios/dist/axios.js"></script>
    -->

    <!--
    <link href="resources/css/base.css" rel="stylesheet">
    -->
    <style type="text/css">

        html, body {
            width: 100%;
            height: 100%;
            padding: 0;
            margin: 0;
            /* overflow: auto; */
        }

        body {
            padding-top: 55px;
            padding-bottom: 55px;
            background-color: #fdfdfd;
            //display: flex;
            flex-wrap: nowrap;
            height: 100vh;
            height: -webkit-fill-available;
            max-height: 100vh;
            overflow-x: auto;
            overflow-y: hidden;
        }

        .main {
            display: flex;
            flex-wrap: nowrap;
            height: 100vh;
            height: -webkit-fill-available;
            max-height: 100vh;
            overflow-x: auto;
            overflow-y: visible;
        }

    </style>

</head>
<body>
<!-- https://cssgradient.io/ -->
<nav class="navbar navbar-expand-lg fixed-top navbar-dark bg-dark" style="background: rgb(181,28,103); background: linear-gradient(90deg, rgba(181,28,103,1) 0%, rgba(181,28,103,1) 39%, rgba(0,168,255,1) 100%);" aria-label="Main navigation">
    <div class="container-fluid">
        <a class="navbar-brand" href="#">Knowledgeroot</a>
        <button class="navbar-toggler p-0 border-0" type="button" id="navbarSideCollapse" aria-label="Toggle navigation">
            <span class="navbar-toggler-icon"></span>
        </button>

        <div class="navbar-collapse offcanvas-collapse" id="navbarsExampleDefault">
            <ul class="navbar-nav me-auto mb-2 mb-lg-0">
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" href="#" id="dropdown01" data-bs-toggle="dropdown" aria-expanded="false"><i class="bi bi-plus-square"></i> New</a>
                    <ul class="dropdown-menu" aria-labelledby="dropdown01">
                        <li><a class="dropdown-item" hx-get="/ui/page/new" hx-target="#content" hx-push-url="true"><i class="bi bi-folder-plus"></i> Page</a></li>
                        <li><a class="dropdown-item" hx-get="/content/new" hx-target="#content"><i class="bi bi-card-text"></i> Content</a></li>
                        <li><a class="dropdown-item" hx-get="/file/new" hx-target="#content"><i class="bi bi-file-plus"></i> File</a></li>
                    </ul>
                </li>
                <!-- user -->
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" href="#" id="navbarDropdownMenuLink2" role="button" data-bs-toggle="dropdown" aria-expanded="false">
                        <i class="bi bi-person"></i> Profile
                    </a>
                    <ul class="dropdown-menu" aria-labelledby="navbarDropdownMenuLink2">
                        <li>
                            <a class="dropdown-item" href="/login">Sign in</a>
                        </li>
                    </ul>
                </li>
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" href="#" id="dropdown01" data-bs-toggle="dropdown" aria-expanded="false"><i class="bi bi-gear-fill"></i> Admin</a>
                    <ul class="dropdown-menu" aria-labelledby="dropdown01">
                        <li><a class="dropdown-item" hx-get="/admin/users" hx-target="#content">Users</a></li>
                        <li><a class="dropdown-item" hx-get="/admin/groups" hx-target="#content">Groups</a></li>
                        <li><a class="dropdown-item" hx-get="/admin/permissions" hx-target="#content">Permissions</a></li>
                    </ul>
                </li>
                <li class="nav-item dropdown">
                    <a class="nav-link dropdown-toggle" href="#" id="dropdown01" data-bs-toggle="dropdown" aria-expanded="false"><i class="bi bi-question-circle"></i></a>
                    <ul class="dropdown-menu" aria-labelledby="dropdown01">
                        <li><a class="dropdown-item" hx-get="/help" hx-target="#content" hx-push-url="true">Help</a></li>
                        <li><a class="dropdown-item" hx-get="/about" hx-target="#content" hx-push-url="true">About</a></li>
                    </ul>
                </li>
            </ul>
            <form class="d-flex" hx-get="/search" hx-target="#content">
                <input class="form-control me-2" type="search" placeholder="Search" aria-label="Search">
                <button class="btn btn-outline-success" type="submit">Search</button>
            </form>
        </div>
    </div>
</nav>

<div class="container-fluid">
    <div class="row main">

        <div class="flex-shrink-0 p-3 bg-white" style="width: 280px;" id="sidebar" hx-get="/ui/sidebar" hx-trigger="load, reload-sidebar from:body" hx-target="#sidebar">


        </div>


        <main role="main" class="col-md-9 ml-sm-auto col-lg-10 px-md-4">
            <div class="" style="padding-top: 20px;" id="content" hx-get="/ui/welcome" hx-trigger="load" hx-target="#content">

            </div><!--/span-->
        </main>
    </div>
</div>

</body>
</html>