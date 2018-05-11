import { Injectable, Pipe, PipeTransform, Output, EventEmitter } from '@angular/core';
import { Router } from '@angular/router';
import { FabricUIService } from '../system/fabric.ui.service';
import { FabricComms } from '../system/fabric.comms.service';
import { UUIDGEN } from '../entities/uuid';
import {ThemeEvent} from "./theme/theme.event.entity";

@Injectable()
export class UIService extends FabricUIService {
    @Output()
    public themeEvent = new EventEmitter<ThemeEvent>();
    public layoutIsSmallScreen:boolean = false;
    public layoutFixedHeader:boolean = true;
    public layoutLayoutHorizontal:boolean = false;
    public layoutLayoutBoxed:boolean = false;

    constructor(private dummyFabricComms:FabricComms, private router:Router) {
        super(dummyFabricComms);
        this.onInit();
    }
    onInit() {
        this.setParent(this.nav.menu, null);
        this.setKeys(this.nav.menu);
    }
    public layoutLoading:boolean = true;
    private title:string = 'welcome';
    private routerLinkArg1:string = '';
    private subTitle:string = 'What is our "fabric"?';
    public setTitle(value:string, routerLinkArg1:string, subTitle:string)
    {
        this.title = value;
        this.routerLinkArg1 = routerLinkArg1;
        this.subTitle = subTitle;
        this.selectViaRouterLink(this.nav.menu)
    }
    public selectViaRouterLink(items:any) {
        let x:UIService = this;
        let finished:boolean = false;
        items.filter(item => {
            if (finished) { return; }
            if (item.routerLink!==undefined && item.routerLink[0]==x.routerLinkArg1) {
                x.select(item, true);
                finished = true;
            }
            if (item.children && !finished) {
                x.selectViaRouterLink(item.children);
            }
        });
    }
    public getTitle():string { return this.title; }
    public getRouterLinkArg1():string { return this.routerLinkArg1; }
    public getSubTitle():string { return this.subTitle; }
    public isLoggedIn:boolean = false;
    public username:string = '';
    // from maverick theme
    private _settings:any = {
        fixedHeader: true,
        headerBarHidden: true,
        leftbarCollapsed: false,
        leftbarShown: false,
        rightbarCollapsed: false,
        fullscreen: false,
        layoutHorizontal: false,
        layoutHorizontalLargeIcons: false,
        layoutBoxed: false,
        showSmallSearchBar: false,
        topNavThemeClass: 'navbar-midnightblue',
        sidebarThemeClass: 'sidebar-default',
        showChatBox: false,
        pageTransitionStyle: 'fadeIn',
        dropdownTransitionStyle: 'flipInX'
    };
    public brandColors:any = {
        'default': '#ecf0f1',

        'inverse': '#95a5a6',
        'primary': '#3498db',
        'success': '#2ecc71',
        'warning': '#f1c40f',
        'danger': '#e74c3c',
        'info': '#1abcaf',

        'brown': '#c0392b',
        'indigo': '#9b59b6',
        'orange': '#e67e22',
        'midnightblue': '#34495e',
        'sky': '#82c4e6',
        'magenta': '#e73c68',
        'purple': '#e044ab',
        'green': '#16a085',
        'grape': '#7a869c',
        'toyo': '#556b8d',
        'alizarin': '#e74c3c'
    };
    public getBrandColor(name:string):string {
        if (this.brandColors[name]) {
            return this.brandColors[name];
        } else {
            return this.brandColors['default'];
        }
    };
    public get(name:string):any {
        return this._settings[name];
    }
    public set(name:string, val):any {
        let changed:boolean = false;
        if (val != this.get(name)) { changed = true; }
        this._settings[name]=val;
        if (changed) { this.themeEvent.emit(ThemeEvent.changed(name)); }
    }
    public toggleLeftBar():void {
        this.set('leftbarCollapsed',!this.get('leftbarCollapsed'));
    }
    public toggleRightBar():void {
        this.set('rightbarCollapsed', !this.get('rightbarCollapsed'));
    }
    public toggleSearchBar():void {
        this.set('showSmallSearchBar', !this.get('showSmallSearchBar'));
    }
    public showHeaderBar():void {
        this.set('headerBarHidden',false);
    }
    public logOut():void {
        this.isLoggedIn = false;
        this.username = '';
    }
    public messageCenter:any = {
        unseenCount:6,
        messages:[{
            name: 'Polly Paton',
            message: 'Uploaded all the files to server',
            time: '3m',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/paton.png',
            read: false
        }, {
            name: 'Simon Corbett',
            message: 'I am signing off for today',
            time: '17m',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/corbett.png',
            read: false
        }, {
            name: 'Matt Tennant',
            message: 'Everything is working just fine here',
            time: '2h',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/tennant.png',
            read: true
        }, {
            name: 'Alan Doyle',
            message: 'Please mail me the files by tonight',
            time: '5d',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/doyle.png',
            read: false
        }, {
            name: 'Polly Paton',
            message: 'Uploaded all the files to server',
            time: '3m',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/paton.png',
            read: false
        }, {
            name: 'Simon Corbett',
            message: 'I am signing off for today',
            time: '17m',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/corbett.png',
            read: false
        }, {
            name: 'Matt Tennant',
            message: 'Everything is working just fine here',
            time: '2h',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/tennant.png',
            read: true
        }, {
            name: 'Alan Doyle',
            message: 'Please mail me the files by tonight',
            time: '5d',
            class: 'notification-danger',
            thumb: 'assets/demo/avatar/doyle.png',
            read: false
        }, ]
    };
    public setRead(item:any) { item.read = true; this.messageCenter.unseenCount++; }
    public setUnread(item:any) { item.read = false; this.messageCenter.unseenCount--; }
    public setReadAll() {
        for (var i = 0, l = this.messageCenter.messages.length; i < l; ++i) { this.messageCenter.messages[i].read = true; } this.messageCenter.unseenCount = 0;
    }
    public notificationCenter:any = {
        unseenCount:5,
        notifications:[{
            text: 'Server #1 is live',
            time: '4m',
            class: 'notification-success',
            iconClasses: 'glyphicon glyphicon-ok',
            seen: true
        }, {
            text: 'New user Registered',
            time: '10m',
            class: 'notification-user',
            iconClasses: 'glyphicon glyphicon-user',
            seen: false
        }, {
            text: 'CPU at 92% on server#3!',
            time: '22m',
            class: 'notification-danger',
            iconClasses: 'glyphicon glyphicon-exclamation-sign',
            seen: false
        }, {
            text: 'Database overloaded',
            time: '30m',
            class: 'notification-warning',
            iconClasses: 'glyphicon glyphicon-warning-sign',
            seen: false
        }, {
            text: 'New order received',
            time: '1h',
            class: 'notification-order',
            iconClasses: 'glyphicon glyphicon-shopping-cart',
            seen: true
        }, {
            text: 'Application error!',
            time: '9d',
            class: 'notification-danger',
            iconClasses: 'glyphicon glyphicon-remove',
            seen: true
        }, {
            text: 'Installation Succeeded',
            time: '1d',
            class: 'notification-success',
            iconClasses: 'glyphicon glyphicon-ok',
            seen: false
        }, {
            text: 'Account Created',
            time: '2d',
            class: 'notification-success',
            iconClasses: 'glyphicon glyphicon-ok',
            seen: false
        }, ]
    };
    public setSeen(item:any) { item.seen = true; this.notificationCenter.unseenCount++; }
    public setUnseen(item:any) { item.seen = false; this.notificationCenter.unseenCount--; }
    public setSeenAll() {
        for (var i = 0, l = this.notificationCenter.notifications.length; i < l; ++i) { this.notificationCenter.notifications[i].seen = true; } this.notificationCenter.unseenCount = 0;
    }

    public setParent(children, parent) {
        let x = this;
        children.filter(child => {
            child.parent = parent;
            if (child.children !== undefined) {
                x.setParent(child.children, child);
            }
            return child;
        });
    }
    public setKeys(children) {
        let x = this;
        children.filter(child => {
            let key = new UUIDGEN(null).getValue();
            child.key = key;
            if (child.children !== undefined) {
                x.setKeys(child.children);
            }
            return child;
        })
    }
    public select(item:any, noNavigate?:boolean) {
        if (!noNavigate && item.routerLink!==undefined) {
            this.router.navigate(['/'+item.routerLink[0]]); // FIXME
        }
        // close open nodes
        if (this.isOpen(item)) { this.setOpen(item,false); return; }
        for (var i = this.nav.state.openItems.length - 1; i >= 0; --i) {
            this.setOpen(this.nav.state.openItems[i],false);
        }
        this.nav.state.openItems=[];
        var parentRef = item;
        while (parentRef) {
            this.setOpen(parentRef,true);
            this.nav.state.openItems.push(parentRef);
            parentRef = parentRef.parent;
        }
        // handle leaf nodes
        if (!item.children || (item.children && item.children.length < 1)) {
            this.nav.state.selectedFromNavMenu = true;
            for (var j = this.nav.state.selectedItems.length - 1; j >= 0; --j) {
                this.setSelected(this.nav.state.selectedItems[j], false);
            }
            this.nav.state.selectedItems = [];
            parentRef = item;
            while (parentRef) {
                this.setSelected(parentRef, true);
                this.nav.state.selectedItems.push(parentRef);
                parentRef = parentRef.parent;
            }
        }
    }
    public highlight(item) {
        var parentRef = item;
        while (parentRef) {
            if (this.isSelected(parentRef)) {
                parentRef = parentRef.parent;// ???null;
                continue;
            }
            this.setSelected(parentRef,true);
            this.nav.state.highlightedItems.push(parentRef);
            parentRef = parentRef.parent;
        }
    }
    public highlightItems(children, query) {
        children.filter(child => {
            if (child.label.toLowerCase().indexOf(query) > -1) {
                this.highlight(child);
            }
            if (child.children !== undefined) {
                this.highlightItems(child.children, query);
            }
        });
    }
    public isOpen(node:any):boolean {
        return this.nav.state.openByKey[node.key]||false;
    }
    public setOpen(node:any, value:boolean):void {
        this.nav.state.openByKey[node.key] = value;
    }
    public isSelected(node:any):boolean {
        return this.nav.state.selectedByKey[node.key]||false;
    }
    public setSelected(node:any, value:boolean):void {
        this.nav.state.selectedByKey[node.key] = value;
    }
    public nav:any = {
        state: {
            openItems: [],
            selectedItems:[],
            selectedFromNavMenu:false,
            highlightedItems: [],
            openByKey: {},
            selectedByKey: {}
        },
        menu:[
            {
                label:'Home',
                iconClasses:'fa fa-1x fa-superpowers',
                routerLink:['']
            },
            {
                label:'DTN',
                iconClasses:'fa fa-1x fa-cloud',
                children: [
                    {
                        label:'Config',
                        iconClasses:'fa fa-1x fa-cogs',
                        routerLink:['dtn-config']
                    },
                    {
                        label:'Node Monitor',
                        iconClasses:'fa fa-1x fa-television',
                        routerLink:['node-monitor']
                    }
                ]

            },
            {
                label:'IoT',
                iconClasses:'fa fa-1x fa-magic',
                children: [
                    {
                        label:'Local',
                        iconClasses:'fa fa-1x fa-leaf',
                        routerLink:['app-local']
                    },
                    {
                        label:'Area',
                        iconClasses:'fa fa-1x fa-map',
                        routerLink:['app-area']
                    },
                    {
                        label:'Global',
                        iconClasses:'fa fa-1x fa-globe',
                        routerLink:['app-global']
                    }
                ]
            }
        ],
        exampleMenu:[{
            label: 'Overview',
            iconClasses: '',
            separator: true
        }, {
            label: 'Dashboard',
            iconClasses: 'glyphicon glyphicon-home',
            url: '#/'
        }, {
            label: 'Layouts',
            iconClasses: 'glyphicon glyphicon-th-list',
            html: '<span class="badge badge-warning">2</span>',
            children: [{
                label: 'Grids',
                url: '#/layout-grid'
            }, {
                label: 'Horizontal Navigation',
                url: '#/layout-horizontal'
            }, {
                label: 'Horizontal Navigation 2',
                url: '#/layout-horizontal2'
            }, {
                label: 'Fixed Boxed Layout',
                url: '#/layout-fixed'
            }]
        }, {
            label: 'Bonus Apps',
            iconClasses: '',
            separator: true
        }, {
            label: 'Email',
            iconClasses: 'glyphicon glyphicon-inbox',
            html: '<span class="badge badge-indigo">3</span>',
            children: [{
                label: 'Inbox',
                url: '#/inbox'
            }, {
                label: 'Compose',
                url: '#/compose-mail'
            }, {
                label: 'Read',
                url: '#/read-mail'
            }, {
                label: 'Email Templates',
                url: '#/extras-email-templates'
            }]
        }, {
            label: 'Tasks',
            iconClasses: 'glyphicon glyphicon-ok',
            html: '<span class="badge badge-info">7</span>',
            url: '#/tasks'
        }, {
            label: 'Calendar',
            iconClasses: 'glyphicon glyphicon-calendar',
            url: '#/calendar'
        }, {
            label: 'Gallery',
            iconClasses: 'glyphicon glyphicon-th-large',
            url: '#/gallery'
        }, {
            label: 'Features',
            iconClasses: 'fa fa-home',
            separator: true
        }, {
            label: 'UI Kit',
            iconClasses: 'glyphicon glyphicon-random',
            children: [{
                label: 'Typography',
                url: '#/ui-typography'
            }, {
                label: 'Buttons',
                url: '#/ui-buttons'
            }, {
                label: 'Tables',
                url: '#/tables-basic'
            }, {
                label: 'Forms',
                url: '#/form-layout'
            }, {
                label: 'Tiles',
                url: '#/ui-tiles'
            }, {
                label: 'Modals & Bootbox',
                url: '#/ui-modals'
            }, {
                label: 'Progress Bars',
                url: '#/ui-progressbars'
            }, {
                label: 'Pagers & Pagination',
                url: '#/ui-paginations'
            }, {
                label: 'Labels & Badges',
                url: '#/ui-labelsbadges'
            }, {
                label: 'Alerts & Notifications',
                url: '#/ui-alerts'
            }, {
                label: 'Sliders & Ranges',
                url: '#/ui-sliders'
            }, {
                label: 'Tabs & Accordions',
                url: '#/ui-tabs'
            }, {
                label: 'Nestable Lists',
                url: '#/ui-nestable'
            }, {
                label: 'Misc',
                url: '#/ui-misc'
            }]
        }, {
            label: 'Panels',
            iconClasses: 'glyphicon glyphicon-cog',
            url: '#/ui-panels',
            html: '<span class="label label-danger">HOT</span>'
        }, {
            label: 'Tables',
            iconClasses: 'glyphicon glyphicon-unchecked',
            children: [{
                label: 'ngGrid',
                url: '#/tables-data'
            }, {
                label: 'Responsive Tables',
                url: '#/tables-responsive'
            }, {
                label: 'Editable Tables',
                url: '#/tables-editable'
            }]
        }, {
            label: 'Forms',
            iconClasses: 'glyphicon glyphicon-check',
            children: [{
                label: 'Components',
                url: '#/form-components'
            }, {
                label: 'Wizards',
                url: '#/form-wizard'
            }, {
                label: 'Validation',
                url: '#/form-validation'
            }, {
                label: 'Masks',
                url: '#/form-masks'
            }, {
                label: 'Multiple File Uploads',
                url: '#/form-fileupload'
            }, {
                label: 'WYSIWYG Editor',
                url: '#/form-wysiwyg'
            }, {
                label: 'Inline Editor',
                url: '#/form-xeditable'
            }, {
                label: 'Image Cropping',
                url: '#/form-imagecrop'
            }]
        }, {
            label: 'Charts',
            iconClasses: 'glyphicon glyphicon-stats',
            hideOnHorizontal: true,
            children: [{
                label: 'Flot',
                url: '#/charts-flot'
            }, {
                label: 'Morris.js',
                url: '#/charts-morrisjs'
            }, {
                label: 'Chart.js',
                url: '#/charts-chartjs'
            }, {
                label: 'nvd3 Charts',
                url: '#/charts-nvd3'
            }, {
                label: 'Sparklines',
                url: '#/charts-sparklines'
            }]
        }, {
            label: 'Maps',
            iconClasses: 'glyphicon glyphicon-map-marker',
            hideOnHorizontal: true,
            children: [{
                label: 'Google Maps',
                url: '#/maps-google'
            }, {
                label: 'Vector Maps',
                url: '#/maps-vector'
            }]
        }, {
            label: 'Pages',
            iconClasses: 'glyphicon glyphicon-file',
            hideOnHorizontal: true,
            children: [{
                label: 'Blank Page',
                url: '#/extras-blank'
            }, {
                label: 'Timeline Split',
                url: '#/extras-timeline2'
            }, {
                label: 'Invoice',
                url: '#/extras-invoice'
            }, {
                label: 'Profile',
                url: '#/extras-profile'
            }, {
                label: 'Search Page',
                url: '#/extras-search'
            }, {
                label: 'Registration',
                url: '#/extras-registration'
            }, {
                label: 'Sign Up',
                url: '#/extras-signupform'
            }, {
                label: 'Password Reset',
                url: '#/extras-forgotpassword'
            }, {
                label: 'Login 1',
                url: '#/extras-login'
            }, {
                label: 'Login 2',
                url: '#/extras-login2'
            }, {
                label: '404 Page',
                url: '#/extras-404'
            }, {
                label: '500 Page',
                url: '#/extras-500'
            }]
        }, {
            label: 'Font Icons',
            iconClasses: 'glyphicon glyphicon-fire',
            hideOnHorizontal: true,
            children: [{
                label: 'Font Awesome',
                url: '#/icons-fontawesome'
            }, {
                label: 'Glyphicons',
                url: '#/icons-glyphicons'
            }]
        }, {
            label: 'Unlimited Level Menu',
            iconClasses: 'glyphicon glyphicon-align-left',
            hideOnHorizontal: true,
            children: [{
                label: 'Menu Item 1'
            }, {
                label: 'Menu Item 2',
                children: [{
                    label: 'Menu Item 2.1'
                }, {
                    label: 'Menu Item 2.1',
                    children: [{
                        label: 'Menu Item 2.1.1'
                    }, {
                        label: 'Menu Item 2.1.2',
                        children: [{
                            label: 'And Deeper Yet!'
                        }]
                    }]
                }]
            }
            ]
        }]
    };
    public searchQuery:string = '';
    public oldSearchQuery = '';
    public hasSearchQuery() { return this.searchQuery.length > 0;}
    public goToSearch() {
        //console.log(this.searchQuery);
    }
    public delayFilter() {
        let x:UIService = this;
        setTimeout(function(){x.runSearch()},100);
    }
    public runSearch() {
        //console.log(this.searchQuery);

        let newVal = this.searchQuery;
        let newValOriginal = newVal;
        let oldVal = this.oldSearchQuery;
        let $scope = this.nav.state;
        newVal = newVal.trim().toLowerCase();
        oldVal = oldVal.trim().toLowerCase();

        {
            var currentPath = this.getRouterLinkArg1();
            if (newVal === '') {
                if ($scope.highlightedItems.length>0) {
                    for (var i = $scope.highlightedItems.length - 1; i >= 0; i--) {
                        if (this.isSelected($scope.highlightedItems[i])) {
                            if ($scope.highlightedItems[i] && $scope.highlightedItems[i].routerLink[0] !== currentPath) {
                                this.setSelected($scope.highlightedItems[i], false);
                            }
                        }
                    }
                }
                $scope.highlightedItems = [];
            } else
            if (newVal !== oldVal) {
                if ($scope.highlightedItems.length>0) {
                    for (var j = $scope.highlightedItems.length - 1; j >= 0; j--) {
                        if (this.isSelected($scope.highlightedItems[j])) {
                            this.setSelected($scope.highlightedItems[j], false);
                        }
                    }
                    $scope.highlightedItems = [];
                }
                this.highlightItems(this.nav.menu, newVal);
            }
        }

        this.oldSearchQuery = newValOriginal;
    }
    public infobar:any = {
        rightbarAccordionsShowOne:true,
        rightbarAccordions:[{open:false},{open:false},{open:false},{open:false},{open:false}],
        chatters:[{
            id: 0,
            status: 'online',
            avatar: 'potter.png',
            name: 'Jeremy Potter'
        }, {
            id: 1,
            status: 'online',
            avatar: 'tennant.png',
            name: 'David Tennant'
        }, {
            id: 2,
            status: 'online',
            avatar: 'johansson.png',
            name: 'Anna Johansson'
        }, {
            id: 3,
            status: 'busy',
            avatar: 'jackson.png',
            name: 'Eric Jackson'
        }, {
            id: 4,
            status: 'online',
            avatar: 'jobs.png',
            name: 'Howard Jobs'
        }, {
            id: 5,
            status: 'online',
            avatar: 'potter.png',
            name: 'Jeremy Potter'
        }, {
            id: 6,
            status: 'away',
            avatar: 'tennant.png',
            name: 'David Tennant'
        }, {
            id: 7,
            status: 'away',
            avatar: 'johansson.png',
            name: 'Anna Johansson'
        }, {
            id: 8,
            status: 'online',
            avatar: 'jackson.png',
            name: 'Eric Jackson'
        }, {
            id: 9,
            status: 'online',
            avatar: 'jobs.png',
            name: 'Howard Jobs'
        }],
        currentChatterId:null
    };
    public toggleChatBox(user:any) { }
    public chatbox:any = {
        messages:[{class:'x-msg-class',avatar:'jobs.png',text:'message #1'}],
        userAvatar:'tennant.jpg',
        userText:'',
        userTyping:true
    };
    public sendMessage(userText:string) {
        this.chatbox.messages.add({class:'x-msg-class',avatar:this.chatbox.userAvatar,userText:userText});
        this.chatbox.userText = false;
    }
}

@Pipe({
    name: 'horizontalNavFilter',
    pure: false
})
@Injectable()
export class HorizontalNavFilterPipe implements PipeTransform {
    transform(items: any[], args: any[]): any {
        // filter items array, items which match and return true will be kept, false will be filtered out
        return items.filter(item => !item.hideOnHorizontal);
    }
}
