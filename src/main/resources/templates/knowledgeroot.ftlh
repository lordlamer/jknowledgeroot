<!DOCTYPE html>
<html lang="en">
<head>

    <meta charset="utf-8">
    <title>Knowledgeroot</title>
    <meta name="description" content="Knowledgeroot Knowledgebase">
    <meta name="author" content="Knowledgeroot Team">

    <!-- Le HTML5 shim, for IE6-8 support of HTML elements -->
    <!--[if lt IE 9]>
    <script src="http://html5shim.googlecode.com/svn/trunk/html5.js"></script>
    <![endif]-->

    <!-- Bootstrap -->
    <link rel="stylesheet" href="webjars/bootstrap/css/bootstrap.min.css">

    <!-- Font Awesome -->
    <link rel="stylesheet" href="webjars/font-awesome/css/fontawesome.min.css">
    <link rel="stylesheet" href="webjars/font-awesome/css/solid.min.css">

    <link href="resources/css/base.css" rel="stylesheet">
    <style type="text/css">

        .colorme {
            /* IE10+ */
            background-image: -ms-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* Mozilla Firefox */
            background-image: -moz-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* Opera */
            background-image: -o-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* Webkit (Safari/Chrome 10) */
            background-image: -webkit-gradient(linear, right top, left bottom, color-stop(0, #B0FFE2), color-stop(100, #23538A));

            /* Webkit (Chrome 11+) */
            background-image: -webkit-linear-gradient(top right, #B0FFE2 0%, #23538A 100%);

            /* W3C Markup */
            background-image: linear-gradient(to bottom left, #B0FFE2 0%, #23538A 100%);
        }

        html, body {
            width: 100%;
            height: 100%;
            padding: 0;
            margin: 0;
            overflow: auto;
        }

        body {
            padding-top: 60px;
            padding-bottom: 60px;
            background-color: #fdfdfd;
        }

        /* Footer
        -------------------------------------------------- */
        .footer {
            background-color: #eee;
            min-width: 940px;
            padding: 30px 0;
            text-shadow: 0 1px 0 #fff;
            border-top: 1px solid #e5e5e5;
            -webkit-box-shadow: inset 0 5px 15px rgba(0,0,0,.025);
            -moz-box-shadow: inset 0 5px 15px rgba(0,0,0,.025);
            /*          box-shadow: inset 0 5px 15px rgba(0,0,0,.025);
            */}
        .footer p {
            color: #555;
        }

    </style>

    <!-- jQuery -->
    <script src="webjars/jquery/jquery.min.js"></script>

    <!-- jQuery ui -->
    <script src="webjars/jquery-ui/jquery-ui.min.js"></script>

    <!-- Bootstrap -->
    <script src="webjars/bootstrap/js/bootstrap.min.js"></script>

    <!-- AngularJS -->
    <script src="webjars/angularjs/angular.min.js"></script>

    <!-- jstree -->
    <link rel="stylesheet" href="webjars/jstree/themes/default/style.min.css" />
    <script src="webjars/jstree/jstree.min.js"></script>

    <link type="text/css" rel="stylesheet" href="resources/jquery-layout/layout-default-latest.css" />
</head>
<body>

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
</nav>
<div class="container-fluid">
    <div class="row">
        <nav id="sidebarMenu" class="col-md-3 col-lg-2 d-md-block bg-light sidebar collapse">
            <div class="sidebar-sticky pt-3">

                <!--
                <div class="row">
                    <div class="col-md-4 col-sm-8 col-xs-8">
                        <button type="button" class="btn btn-success btn-sm" onclick="demo_create();"><i class="glyphicon glyphicon-asterisk"></i> Create</button>
                        <button type="button" class="btn btn-warning btn-sm" onclick="demo_rename();"><i class="glyphicon glyphicon-pencil"></i> Rename</button>
                        <button type="button" class="btn btn-danger btn-sm" onclick="demo_delete();"><i class="glyphicon glyphicon-remove"></i> Delete</button>
                    </div>
                    <div class="col-md-2 col-sm-4 col-xs-4" style="text-align:right;">
                        <input type="text" value="" style="box-shadow:inset 0 0 4px #eee; width:120px; margin:0; padding:6px 12px; border-radius:4px; border:1px solid silver; font-size:1.1em;" id="demo_q" placeholder="Search" />
                    </div>
                </div>
                -->

                <div id="treeOne"></div>

                <script type="text/javascript">
                    function demo_create() {
                        var ref = $('#treeOne').jstree(true),
                            sel = ref.get_selected();
                        if(!sel.length) { return false; }
                        sel = sel[0];
                        sel = ref.create_node(sel, {"type":"file"});
                        if(sel) {
                            ref.edit(sel);
                        }
                    };
                    function demo_rename() {
                        var ref = $('#treeOne').jstree(true),
                            sel = ref.get_selected();
                        if(!sel.length) { return false; }
                        sel = sel[0];
                        ref.edit(sel);
                    };
                    function demo_delete() {
                        var ref = $('#treeOne').jstree(true),
                            sel = ref.get_selected();
                        if(!sel.length) { return false; }
                        ref.delete_node(sel);
                    };
                    $(function () {
                        var to = false;
                        $('#demo_q').keyup(function () {
                            if(to) { clearTimeout(to); }
                            to = setTimeout(function () {
                                var v = $('#demo_q').val();
                                $('#jstree_demo').jstree(true).search(v);
                            }, 250);
                        });

                        $('#treeOne')
                            .jstree({
                                "core" : {
                                    "animation" : 0,
                                    "check_callback" : true,
                                    'force_text' : true,
                                    "themes" : { "stripes" : true },
                                    'data' : {
                                        'url' : function (node) {
                                            return node.id === '#' ? 'resources/roots.json' : 'resources/tree.json';
                                        },
                                        'data' : function (node) {
                                            return { 'id' : node.id };
                                        }
                                    }
                                },
                                "types" : {
                                    "#" : { "max_children" : 1, "max_depth" : 4, "valid_children" : ["root"] },
                                    "root" : { "icon" : "/static/3.3.11/assets/images/tree_icon.png", "valid_children" : ["default"] },
                                    "default" : { "valid_children" : ["default","file"] },
                                    "file" : { "icon" : "glyphicon glyphicon-file", "valid_children" : [] }
                                },
                                "plugins" : [ "contextmenu", "dnd", "search", "state", "types", "wholerow" ]
                            });
                    });

                </script>
            </div>
        </nav>

        <main role="main" class="col-md-9 ml-sm-auto col-lg-10 px-md-4">
            <script>
                $(".alert").alert()
            </script>
            <div class="">
                <nav aria-label="breadcrumb">
                    <ol class="breadcrumb">
                        <li class="breadcrumb-item"><i class="fa fa-home fa-lg"></i> <a href="#">Home</a></li>
                        <li class="breadcrumb-item"><a href="#">Library</a></li>
                        <li class="breadcrumb-item active" aria-current="page">Data</li>
                    </ol>
                </nav>

                <div class="jumbotron">
                    <h1 class="display-4">Hello, world!</h1>
                    <p class="lead">This is a simple hero unit, a simple jumbotron-style component for calling extra attention to featured content or information.</p>
                    <hr class="my-4">
                    <p>It uses utility classes for typography and spacing to space content out within the larger container.</p>
                    <a class="btn btn-primary btn-lg" href="#" role="button">Learn more</a>
                </div>

                <div class="card">
                    <div class="card-header">
                        <ul class="nav nav-pills card-header-pills">
                            <li class="nav-item">
                                <a class="nav-link active" href="#">new</a>
                            </li>
                        </ul>
                    </div>
                    <div class="card-body">
                        <table class="table table-sm">
                            <thead>
                            <tr>
                                <th scope="col">#</th>
                                <th scope="col">First</th>
                                <th scope="col">Last</th>
                                <th scope="col">Handle</th>
                            </tr>
                            </thead>
                            <tbody>
                            <tr>
                                <th scope="row">1</th>
                                <td>Mark</td>
                                <td>Otto</td>
                                <td>@mdo</td>
                            </tr>
                            <tr>
                                <th scope="row">2</th>
                                <td>Jacob</td>
                                <td>Thornton</td>
                                <td>@fat</td>
                            </tr>
                            <tr>
                                <th scope="row">3</th>
                                <td colspan="2">Larry the Bird</td>
                                <td>@twitter</td>
                            </tr>
                            </tbody>
                        </table>
                    </div>
                </div>


                <div class="my-3 p-3 bg-white rounded box-shadow">
                    <h6 class="border-bottom border-gray pb-2 mb-0">Recent updates</h6>

                    <table class="table table-sm">
                        <thead>
                        <tr>
                            <th scope="col">#</th>
                            <th scope="col">First</th>
                            <th scope="col">Last</th>
                            <th scope="col">Handle</th>
                        </tr>
                        </thead>
                        <tbody>
                        <tr>
                            <th scope="row">1</th>
                            <td>Mark</td>
                            <td>Otto</td>
                            <td>@mdo</td>
                        </tr>
                        <tr>
                            <th scope="row">2</th>
                            <td>Jacob</td>
                            <td>Thornton</td>
                            <td>@fat</td>
                        </tr>
                        <tr>
                            <th scope="row">3</th>
                            <td colspan="2">Larry the Bird</td>
                            <td>@twitter</td>
                        </tr>
                        </tbody>
                    </table>
                </div>
            </div><!--/span-->
        </main>
    </div>
</div>

<!--
<nav class="navbar fixed-bottom navbar-dark bg-dark navbar-expand-lg" style="border-top: 3px solid #F88529;">
    <div class="collapse navbar-collapse">
        <span class="navbar-text">
      Version: 1.0.0
        </span>
    </div>
</nav>
-->

</body>
</html>