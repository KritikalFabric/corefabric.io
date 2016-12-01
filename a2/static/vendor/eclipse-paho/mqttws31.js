/*
 JavaScript BigInteger library version 0.9.1
 http://silentmatt.com/biginteger/

 Copyright (c) 2009 Matthew Crumley <email@matthewcrumley.com>
 Copyright (c) 2010,2011 by John Tobey <John.Tobey@gmail.com>
 Licensed under the MIT license.

 Support for arbitrary internal representation base was added by
 Vitaly Magerya.
 */

/*
 File: biginteger.js

 Exports:

 <BigInteger>
 */
(function(exports) {
	"use strict";
	/*
	 Class: BigInteger
	 An arbitrarily-large integer.

	 <BigInteger> objects should be considered immutable. None of the "built-in"
	 methods modify *this* or their arguments. All properties should be
	 considered private.

	 All the methods of <BigInteger> instances can be called "statically". The
	 static versions are convenient if you don't already have a <BigInteger>
	 object.

	 As an example, these calls are equivalent.

	 > BigInteger(4).multiply(5); // returns BigInteger(20);
	 > BigInteger.multiply(4, 5); // returns BigInteger(20);

	 > var a = 42;
	 > var a = BigInteger.toJSValue("0b101010"); // Not completely useless...
	 */

	var CONSTRUCT = {}; // Unique token to call "private" version of constructor

	/*
	 Constructor: BigInteger()
	 Convert a value to a <BigInteger>.

	 Although <BigInteger()> is the constructor for <BigInteger> objects, it is
	 best not to call it as a constructor. If *n* is a <BigInteger> object, it is
	 simply returned as-is. Otherwise, <BigInteger()> is equivalent to <parse>
	 without a radix argument.

	 > var n0 = BigInteger();      // Same as <BigInteger.ZERO>
	 > var n1 = BigInteger("123"); // Create a new <BigInteger> with value 123
	 > var n2 = BigInteger(123);   // Create a new <BigInteger> with value 123
	 > var n3 = BigInteger(n2);    // Return n2, unchanged

	 The constructor form only takes an array and a sign. *n* must be an
	 array of numbers in little-endian order, where each digit is between 0
	 and BigInteger.base.  The second parameter sets the sign: -1 for
	 negative, +1 for positive, or 0 for zero. The array is *not copied and
	 may be modified*. If the array contains only zeros, the sign parameter
	 is ignored and is forced to zero.

	 > new BigInteger([5], -1): create a new BigInteger with value -5

	 Parameters:

	 n - Value to convert to a <BigInteger>.

	 Returns:

	 A <BigInteger> value.

	 See Also:

	 <parse>, <BigInteger>
	 */
	function BigInteger(n, s, token) {
		if (token !== CONSTRUCT) {
			if (n instanceof BigInteger) {
				return n;
			}
			else if (typeof n === "undefined") {
				return ZERO;
			}
			return BigInteger.parse(n);
		}

		n = n || [];  // Provide the nullary constructor for subclasses.
		while (n.length && !n[n.length - 1]) {
			--n.length;
		}
		this._d = n;
		this._s = n.length ? (s || 1) : 0;
	}

	BigInteger._construct = function(n, s) {
		return new BigInteger(n, s, CONSTRUCT);
	};

// Base-10 speedup hacks in parse, toString, exp10 and log functions
// require base to be a power of 10. 10^7 is the largest such power
// that won't cause a precision loss when digits are multiplied.
	var BigInteger_base = 10000000;
	var BigInteger_base_log10 = 7;

	BigInteger.base = BigInteger_base;
	BigInteger.base_log10 = BigInteger_base_log10;

	var ZERO = new BigInteger([], 0, CONSTRUCT);
// Constant: ZERO
// <BigInteger> 0.
	BigInteger.ZERO = ZERO;

	var ONE = new BigInteger([1], 1, CONSTRUCT);
// Constant: ONE
// <BigInteger> 1.
	BigInteger.ONE = ONE;

	var M_ONE = new BigInteger(ONE._d, -1, CONSTRUCT);
// Constant: M_ONE
// <BigInteger> -1.
	BigInteger.M_ONE = M_ONE;

// Constant: _0
// Shortcut for <ZERO>.
	BigInteger._0 = ZERO;

// Constant: _1
// Shortcut for <ONE>.
	BigInteger._1 = ONE;

	/*
	 Constant: small
	 Array of <BigIntegers> from 0 to 36.

	 These are used internally for parsing, but useful when you need a "small"
	 <BigInteger>.

	 See Also:

	 <ZERO>, <ONE>, <_0>, <_1>
	 */
	BigInteger.small = [
		ZERO,
		ONE,
		/* Assuming BigInteger_base > 36 */
		new BigInteger( [2], 1, CONSTRUCT),
		new BigInteger( [3], 1, CONSTRUCT),
		new BigInteger( [4], 1, CONSTRUCT),
		new BigInteger( [5], 1, CONSTRUCT),
		new BigInteger( [6], 1, CONSTRUCT),
		new BigInteger( [7], 1, CONSTRUCT),
		new BigInteger( [8], 1, CONSTRUCT),
		new BigInteger( [9], 1, CONSTRUCT),
		new BigInteger([10], 1, CONSTRUCT),
		new BigInteger([11], 1, CONSTRUCT),
		new BigInteger([12], 1, CONSTRUCT),
		new BigInteger([13], 1, CONSTRUCT),
		new BigInteger([14], 1, CONSTRUCT),
		new BigInteger([15], 1, CONSTRUCT),
		new BigInteger([16], 1, CONSTRUCT),
		new BigInteger([17], 1, CONSTRUCT),
		new BigInteger([18], 1, CONSTRUCT),
		new BigInteger([19], 1, CONSTRUCT),
		new BigInteger([20], 1, CONSTRUCT),
		new BigInteger([21], 1, CONSTRUCT),
		new BigInteger([22], 1, CONSTRUCT),
		new BigInteger([23], 1, CONSTRUCT),
		new BigInteger([24], 1, CONSTRUCT),
		new BigInteger([25], 1, CONSTRUCT),
		new BigInteger([26], 1, CONSTRUCT),
		new BigInteger([27], 1, CONSTRUCT),
		new BigInteger([28], 1, CONSTRUCT),
		new BigInteger([29], 1, CONSTRUCT),
		new BigInteger([30], 1, CONSTRUCT),
		new BigInteger([31], 1, CONSTRUCT),
		new BigInteger([32], 1, CONSTRUCT),
		new BigInteger([33], 1, CONSTRUCT),
		new BigInteger([34], 1, CONSTRUCT),
		new BigInteger([35], 1, CONSTRUCT),
		new BigInteger([36], 1, CONSTRUCT)
	];

// Used for parsing/radix conversion
	BigInteger.digits = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ".split("");

	/*
	 Method: toString
	 Convert a <BigInteger> to a string.

	 When *base* is greater than 10, letters are upper case.

	 Parameters:

	 base - Optional base to represent the number in (default is base 10).
	 Must be between 2 and 36 inclusive, or an Error will be thrown.

	 Returns:

	 The string representation of the <BigInteger>.
	 */
	BigInteger.prototype.toString = function(base) {
		base = +base || 10;
		if (base < 2 || base > 36) {
			throw new Error("illegal radix " + base + ".");
		}
		if (this._s === 0) {
			return "0";
		}
		if (base === 10) {
			var str = this._s < 0 ? "-" : "";
			str += this._d[this._d.length - 1].toString();
			for (var i = this._d.length - 2; i >= 0; i--) {
				var group = this._d[i].toString();
				while (group.length < BigInteger_base_log10) group = '0' + group;
				str += group;
			}
			return str;
		}
		else {
			var numerals = BigInteger.digits;
			base = BigInteger.small[base];
			var sign = this._s;

			var n = this.abs();
			var digits = [];
			var digit;

			while (n._s !== 0) {
				var divmod = n.divRem(base);
				n = divmod[0];
				digit = divmod[1];
				// TODO: This could be changed to unshift instead of reversing at the end.
				// Benchmark both to compare speeds.
				digits.push(numerals[digit.valueOf()]);
			}
			return (sign < 0 ? "-" : "") + digits.reverse().join("");
		}
	};

// Verify strings for parsing
	BigInteger.radixRegex = [
		/^$/,
		/^$/,
		/^[01]*$/,
		/^[012]*$/,
		/^[0-3]*$/,
		/^[0-4]*$/,
		/^[0-5]*$/,
		/^[0-6]*$/,
		/^[0-7]*$/,
		/^[0-8]*$/,
		/^[0-9]*$/,
		/^[0-9aA]*$/,
		/^[0-9abAB]*$/,
		/^[0-9abcABC]*$/,
		/^[0-9a-dA-D]*$/,
		/^[0-9a-eA-E]*$/,
		/^[0-9a-fA-F]*$/,
		/^[0-9a-gA-G]*$/,
		/^[0-9a-hA-H]*$/,
		/^[0-9a-iA-I]*$/,
		/^[0-9a-jA-J]*$/,
		/^[0-9a-kA-K]*$/,
		/^[0-9a-lA-L]*$/,
		/^[0-9a-mA-M]*$/,
		/^[0-9a-nA-N]*$/,
		/^[0-9a-oA-O]*$/,
		/^[0-9a-pA-P]*$/,
		/^[0-9a-qA-Q]*$/,
		/^[0-9a-rA-R]*$/,
		/^[0-9a-sA-S]*$/,
		/^[0-9a-tA-T]*$/,
		/^[0-9a-uA-U]*$/,
		/^[0-9a-vA-V]*$/,
		/^[0-9a-wA-W]*$/,
		/^[0-9a-xA-X]*$/,
		/^[0-9a-yA-Y]*$/,
		/^[0-9a-zA-Z]*$/
	];

	/*
	 Function: parse
	 Parse a string into a <BigInteger>.

	 *base* is optional but, if provided, must be from 2 to 36 inclusive. If
	 *base* is not provided, it will be guessed based on the leading characters
	 of *s* as follows:

	 - "0x" or "0X": *base* = 16
	 - "0c" or "0C": *base* = 8
	 - "0b" or "0B": *base* = 2
	 - else: *base* = 10

	 If no base is provided, or *base* is 10, the number can be in exponential
	 form. For example, these are all valid:

	 > BigInteger.parse("1e9");              // Same as "1000000000"
	 > BigInteger.parse("1.234*10^3");       // Same as 1234
	 > BigInteger.parse("56789 * 10 ** -2"); // Same as 567

	 If any characters fall outside the range defined by the radix, an exception
	 will be thrown.

	 Parameters:

	 s - The string to parse.
	 base - Optional radix (default is to guess based on *s*).

	 Returns:

	 a <BigInteger> instance.
	 */
	BigInteger.parse = function(s, base) {
		// Expands a number in exponential form to decimal form.
		// expandExponential("-13.441*10^5") === "1344100";
		// expandExponential("1.12300e-1") === "0.112300";
		// expandExponential(1000000000000000000000000000000) === "1000000000000000000000000000000";
		function expandExponential(str) {
			str = str.replace(/\s*[*xX]\s*10\s*(\^|\*\*)\s*/, "e");

			return str.replace(/^([+\-])?(\d+)\.?(\d*)[eE]([+\-]?\d+)$/, function(x, s, n, f, c) {
				c = +c;
				var l = c < 0;
				var i = n.length + c;
				x = (l ? n : f).length;
				c = ((c = Math.abs(c)) >= x ? c - x + l : 0);
				var z = (new Array(c + 1)).join("0");
				var r = n + f;
				return (s || "") + (l ? r = z + r : r += z).substr(0, i += l ? z.length : 0) + (i < r.length ? "." + r.substr(i) : "");
			});
		}

		s = s.toString();
		if (typeof base === "undefined" || +base === 10) {
			s = expandExponential(s);
		}

		var prefixRE;
		if (typeof base === "undefined") {
			prefixRE = '0[xcb]';
		}
		else if (base == 16) {
			prefixRE = '0x';
		}
		else if (base == 8) {
			prefixRE = '0c';
		}
		else if (base == 2) {
			prefixRE = '0b';
		}
		else {
			prefixRE = '';
		}
		var parts = new RegExp('^([+\\-]?)(' + prefixRE + ')?([0-9a-z]*)(?:\\.\\d*)?$', 'i').exec(s);
		if (parts) {
			var sign = parts[1] || "+";
			var baseSection = parts[2] || "";
			var digits = parts[3] || "";

			if (typeof base === "undefined") {
				// Guess base
				if (baseSection === "0x" || baseSection === "0X") { // Hex
					base = 16;
				}
				else if (baseSection === "0c" || baseSection === "0C") { // Octal
					base = 8;
				}
				else if (baseSection === "0b" || baseSection === "0B") { // Binary
					base = 2;
				}
				else {
					base = 10;
				}
			}
			else if (base < 2 || base > 36) {
				throw new Error("Illegal radix " + base + ".");
			}

			base = +base;

			// Check for digits outside the range
			if (!(BigInteger.radixRegex[base].test(digits))) {
				throw new Error("Bad digit for radix " + base);
			}

			// Strip leading zeros, and convert to array
			digits = digits.replace(/^0+/, "").split("");
			if (digits.length === 0) {
				return ZERO;
			}

			// Get the sign (we know it's not zero)
			sign = (sign === "-") ? -1 : 1;

			// Optimize 10
			if (base == 10) {
				var d = [];
				while (digits.length >= BigInteger_base_log10) {
					d.push(parseInt(digits.splice(digits.length-BigInteger.base_log10, BigInteger.base_log10).join(''), 10));
				}
				d.push(parseInt(digits.join(''), 10));
				return new BigInteger(d, sign, CONSTRUCT);
			}

			// Do the conversion
			var d = ZERO;
			base = BigInteger.small[base];
			var small = BigInteger.small;
			for (var i = 0; i < digits.length; i++) {
				d = d.multiply(base).add(small[parseInt(digits[i], 36)]);
			}
			return new BigInteger(d._d, sign, CONSTRUCT);
		}
		else {
			throw new Error("Invalid BigInteger format: " + s);
		}
	};

	/*
	 Function: add
	 Add two <BigIntegers>.

	 Parameters:

	 n - The number to add to *this*. Will be converted to a <BigInteger>.

	 Returns:

	 The numbers added together.

	 See Also:

	 <subtract>, <multiply>, <quotient>, <next>
	 */
	BigInteger.prototype.add = function(n) {
		if (this._s === 0) {
			return BigInteger(n);
		}

		n = BigInteger(n);
		if (n._s === 0) {
			return this;
		}
		if (this._s !== n._s) {
			n = n.negate();
			return this.subtract(n);
		}

		var a = this._d;
		var b = n._d;
		var al = a.length;
		var bl = b.length;
		var sum = new Array(Math.max(al, bl) + 1);
		var size = Math.min(al, bl);
		var carry = 0;
		var digit;

		for (var i = 0; i < size; i++) {
			digit = a[i] + b[i] + carry;
			sum[i] = digit % BigInteger_base;
			carry = (digit / BigInteger_base) | 0;
		}
		if (bl > al) {
			a = b;
			al = bl;
		}
		for (i = size; carry && i < al; i++) {
			digit = a[i] + carry;
			sum[i] = digit % BigInteger_base;
			carry = (digit / BigInteger_base) | 0;
		}
		if (carry) {
			sum[i] = carry;
		}

		for ( ; i < al; i++) {
			sum[i] = a[i];
		}

		return new BigInteger(sum, this._s, CONSTRUCT);
	};

	/*
	 Function: negate
	 Get the additive inverse of a <BigInteger>.

	 Returns:

	 A <BigInteger> with the same magnatude, but with the opposite sign.

	 See Also:

	 <abs>
	 */
	BigInteger.prototype.negate = function() {
		return new BigInteger(this._d, (-this._s) | 0, CONSTRUCT);
	};

	/*
	 Function: abs
	 Get the absolute value of a <BigInteger>.

	 Returns:

	 A <BigInteger> with the same magnatude, but always positive (or zero).

	 See Also:

	 <negate>
	 */
	BigInteger.prototype.abs = function() {
		return (this._s < 0) ? this.negate() : this;
	};

	/*
	 Function: subtract
	 Subtract two <BigIntegers>.

	 Parameters:

	 n - The number to subtract from *this*. Will be converted to a <BigInteger>.

	 Returns:

	 The *n* subtracted from *this*.

	 See Also:

	 <add>, <multiply>, <quotient>, <prev>
	 */
	BigInteger.prototype.subtract = function(n) {
		if (this._s === 0) {
			return BigInteger(n).negate();
		}

		n = BigInteger(n);
		if (n._s === 0) {
			return this;
		}
		if (this._s !== n._s) {
			n = n.negate();
			return this.add(n);
		}

		var m = this;
		// negative - negative => -|a| - -|b| => -|a| + |b| => |b| - |a|
		if (this._s < 0) {
			m = new BigInteger(n._d, 1, CONSTRUCT);
			n = new BigInteger(this._d, 1, CONSTRUCT);
		}

		// Both are positive => a - b
		var sign = m.compareAbs(n);
		if (sign === 0) {
			return ZERO;
		}
		else if (sign < 0) {
			// swap m and n
			var t = n;
			n = m;
			m = t;
		}

		// a > b
		var a = m._d;
		var b = n._d;
		var al = a.length;
		var bl = b.length;
		var diff = new Array(al); // al >= bl since a > b
		var borrow = 0;
		var i;
		var digit;

		for (i = 0; i < bl; i++) {
			digit = a[i] - borrow - b[i];
			if (digit < 0) {
				digit += BigInteger_base;
				borrow = 1;
			}
			else {
				borrow = 0;
			}
			diff[i] = digit;
		}
		for (i = bl; i < al; i++) {
			digit = a[i] - borrow;
			if (digit < 0) {
				digit += BigInteger_base;
			}
			else {
				diff[i++] = digit;
				break;
			}
			diff[i] = digit;
		}
		for ( ; i < al; i++) {
			diff[i] = a[i];
		}

		return new BigInteger(diff, sign, CONSTRUCT);
	};

	(function() {
		function addOne(n, sign) {
			var a = n._d;
			var sum = a.slice();
			var carry = true;
			var i = 0;

			while (true) {
				var digit = (a[i] || 0) + 1;
				sum[i] = digit % BigInteger_base;
				if (digit <= BigInteger_base - 1) {
					break;
				}
				++i;
			}

			return new BigInteger(sum, sign, CONSTRUCT);
		}

		function subtractOne(n, sign) {
			var a = n._d;
			var sum = a.slice();
			var borrow = true;
			var i = 0;

			while (true) {
				var digit = (a[i] || 0) - 1;
				if (digit < 0) {
					sum[i] = digit + BigInteger_base;
				}
				else {
					sum[i] = digit;
					break;
				}
				++i;
			}

			return new BigInteger(sum, sign, CONSTRUCT);
		}

		/*
		 Function: next
		 Get the next <BigInteger> (add one).

		 Returns:

		 *this* + 1.

		 See Also:

		 <add>, <prev>
		 */
		BigInteger.prototype.next = function() {
			switch (this._s) {
				case 0:
					return ONE;
				case -1:
					return subtractOne(this, -1);
				// case 1:
				default:
					return addOne(this, 1);
			}
		};

		/*
		 Function: prev
		 Get the previous <BigInteger> (subtract one).

		 Returns:

		 *this* - 1.

		 See Also:

		 <next>, <subtract>
		 */
		BigInteger.prototype.prev = function() {
			switch (this._s) {
				case 0:
					return M_ONE;
				case -1:
					return addOne(this, -1);
				// case 1:
				default:
					return subtractOne(this, 1);
			}
		};
	})();

	/*
	 Function: compareAbs
	 Compare the absolute value of two <BigIntegers>.

	 Calling <compareAbs> is faster than calling <abs> twice, then <compare>.

	 Parameters:

	 n - The number to compare to *this*. Will be converted to a <BigInteger>.

	 Returns:

	 -1, 0, or +1 if *|this|* is less than, equal to, or greater than *|n|*.

	 See Also:

	 <compare>, <abs>
	 */
	BigInteger.prototype.compareAbs = function(n) {
		if (this === n) {
			return 0;
		}

		if (!(n instanceof BigInteger)) {
			if (!isFinite(n)) {
				return(isNaN(n) ? n : -1);
			}
			n = BigInteger(n);
		}

		if (this._s === 0) {
			return (n._s !== 0) ? -1 : 0;
		}
		if (n._s === 0) {
			return 1;
		}

		var l = this._d.length;
		var nl = n._d.length;
		if (l < nl) {
			return -1;
		}
		else if (l > nl) {
			return 1;
		}

		var a = this._d;
		var b = n._d;
		for (var i = l-1; i >= 0; i--) {
			if (a[i] !== b[i]) {
				return a[i] < b[i] ? -1 : 1;
			}
		}

		return 0;
	};

	/*
	 Function: compare
	 Compare two <BigIntegers>.

	 Parameters:

	 n - The number to compare to *this*. Will be converted to a <BigInteger>.

	 Returns:

	 -1, 0, or +1 if *this* is less than, equal to, or greater than *n*.

	 See Also:

	 <compareAbs>, <isPositive>, <isNegative>, <isUnit>
	 */
	BigInteger.prototype.compare = function(n) {
		if (this === n) {
			return 0;
		}

		n = BigInteger(n);

		if (this._s === 0) {
			return -n._s;
		}

		if (this._s === n._s) { // both positive or both negative
			var cmp = this.compareAbs(n);
			return cmp * this._s;
		}
		else {
			return this._s;
		}
	};

	/*
	 Function: isUnit
	 Return true iff *this* is either 1 or -1.

	 Returns:

	 true if *this* compares equal to <BigInteger.ONE> or <BigInteger.M_ONE>.

	 See Also:

	 <isZero>, <isNegative>, <isPositive>, <compareAbs>, <compare>,
	 <BigInteger.ONE>, <BigInteger.M_ONE>
	 */
	BigInteger.prototype.isUnit = function() {
		return this === ONE ||
			this === M_ONE ||
			(this._d.length === 1 && this._d[0] === 1);
	};

	/*
	 Function: multiply
	 Multiply two <BigIntegers>.

	 Parameters:

	 n - The number to multiply *this* by. Will be converted to a
	 <BigInteger>.

	 Returns:

	 The numbers multiplied together.

	 See Also:

	 <add>, <subtract>, <quotient>, <square>
	 */
	BigInteger.prototype.multiply = function(n) {
		// TODO: Consider adding Karatsuba multiplication for large numbers
		if (this._s === 0) {
			return ZERO;
		}

		n = BigInteger(n);
		if (n._s === 0) {
			return ZERO;
		}
		if (this.isUnit()) {
			if (this._s < 0) {
				return n.negate();
			}
			return n;
		}
		if (n.isUnit()) {
			if (n._s < 0) {
				return this.negate();
			}
			return this;
		}
		if (this === n) {
			return this.square();
		}

		var r = (this._d.length >= n._d.length);
		var a = (r ? this : n)._d; // a will be longer than b
		var b = (r ? n : this)._d;
		var al = a.length;
		var bl = b.length;

		var pl = al + bl;
		var partial = new Array(pl);
		var i;
		for (i = 0; i < pl; i++) {
			partial[i] = 0;
		}

		for (i = 0; i < bl; i++) {
			var carry = 0;
			var bi = b[i];
			var jlimit = al + i;
			var digit;
			for (var j = i; j < jlimit; j++) {
				digit = partial[j] + bi * a[j - i] + carry;
				carry = (digit / BigInteger_base) | 0;
				partial[j] = (digit % BigInteger_base) | 0;
			}
			if (carry) {
				digit = partial[j] + carry;
				carry = (digit / BigInteger_base) | 0;
				partial[j] = digit % BigInteger_base;
			}
		}
		return new BigInteger(partial, this._s * n._s, CONSTRUCT);
	};

// Multiply a BigInteger by a single-digit native number
// Assumes that this and n are >= 0
// This is not really intended to be used outside the library itself
	BigInteger.prototype.multiplySingleDigit = function(n) {
		if (n === 0 || this._s === 0) {
			return ZERO;
		}
		if (n === 1) {
			return this;
		}

		var digit;
		if (this._d.length === 1) {
			digit = this._d[0] * n;
			if (digit >= BigInteger_base) {
				return new BigInteger([(digit % BigInteger_base)|0,
					(digit / BigInteger_base)|0], 1, CONSTRUCT);
			}
			return new BigInteger([digit], 1, CONSTRUCT);
		}

		if (n === 2) {
			return this.add(this);
		}
		if (this.isUnit()) {
			return new BigInteger([n], 1, CONSTRUCT);
		}

		var a = this._d;
		var al = a.length;

		var pl = al + 1;
		var partial = new Array(pl);
		for (var i = 0; i < pl; i++) {
			partial[i] = 0;
		}

		var carry = 0;
		for (var j = 0; j < al; j++) {
			digit = n * a[j] + carry;
			carry = (digit / BigInteger_base) | 0;
			partial[j] = (digit % BigInteger_base) | 0;
		}
		if (carry) {
			partial[j] = carry;
		}

		return new BigInteger(partial, 1, CONSTRUCT);
	};

	/*
	 Function: square
	 Multiply a <BigInteger> by itself.

	 This is slightly faster than regular multiplication, since it removes the
	 duplicated multiplcations.

	 Returns:

	 > this.multiply(this)

	 See Also:
	 <multiply>
	 */
	BigInteger.prototype.square = function() {
		// Normally, squaring a 10-digit number would take 100 multiplications.
		// Of these 10 are unique diagonals, of the remaining 90 (100-10), 45 are repeated.
		// This procedure saves (N*(N-1))/2 multiplications, (e.g., 45 of 100 multiplies).
		// Based on code by Gary Darby, Intellitech Systems Inc., www.DelphiForFun.org

		if (this._s === 0) {
			return ZERO;
		}
		if (this.isUnit()) {
			return ONE;
		}

		var digits = this._d;
		var length = digits.length;
		var imult1 = new Array(length + length + 1);
		var product, carry, k;
		var i;

		// Calculate diagonal
		for (i = 0; i < length; i++) {
			k = i * 2;
			product = digits[i] * digits[i];
			carry = (product / BigInteger_base) | 0;
			imult1[k] = product % BigInteger_base;
			imult1[k + 1] = carry;
		}

		// Calculate repeating part
		for (i = 0; i < length; i++) {
			carry = 0;
			k = i * 2 + 1;
			for (var j = i + 1; j < length; j++, k++) {
				product = digits[j] * digits[i] * 2 + imult1[k] + carry;
				carry = (product / BigInteger_base) | 0;
				imult1[k] = product % BigInteger_base;
			}
			k = length + i;
			var digit = carry + imult1[k];
			carry = (digit / BigInteger_base) | 0;
			imult1[k] = digit % BigInteger_base;
			imult1[k + 1] += carry;
		}

		return new BigInteger(imult1, 1, CONSTRUCT);
	};

	/*
	 Function: quotient
	 Divide two <BigIntegers> and truncate towards zero.

	 <quotient> throws an exception if *n* is zero.

	 Parameters:

	 n - The number to divide *this* by. Will be converted to a <BigInteger>.

	 Returns:

	 The *this* / *n*, truncated to an integer.

	 See Also:

	 <add>, <subtract>, <multiply>, <divRem>, <remainder>
	 */
	BigInteger.prototype.quotient = function(n) {
		return this.divRem(n)[0];
	};

	/*
	 Function: divide
	 Deprecated synonym for <quotient>.
	 */
	BigInteger.prototype.divide = BigInteger.prototype.quotient;

	/*
	 Function: remainder
	 Calculate the remainder of two <BigIntegers>.

	 <remainder> throws an exception if *n* is zero.

	 Parameters:

	 n - The remainder after *this* is divided *this* by *n*. Will be
	 converted to a <BigInteger>.

	 Returns:

	 *this* % *n*.

	 See Also:

	 <divRem>, <quotient>
	 */
	BigInteger.prototype.remainder = function(n) {
		return this.divRem(n)[1];
	};

	/*
	 Function: divRem
	 Calculate the integer quotient and remainder of two <BigIntegers>.

	 <divRem> throws an exception if *n* is zero.

	 Parameters:

	 n - The number to divide *this* by. Will be converted to a <BigInteger>.

	 Returns:

	 A two-element array containing the quotient and the remainder.

	 > a.divRem(b)

	 is exactly equivalent to

	 > [a.quotient(b), a.remainder(b)]

	 except it is faster, because they are calculated at the same time.

	 See Also:

	 <quotient>, <remainder>
	 */
	BigInteger.prototype.divRem = function(n) {
		n = BigInteger(n);
		if (n._s === 0) {
			throw new Error("Divide by zero");
		}
		if (this._s === 0) {
			return [ZERO, ZERO];
		}
		if (n._d.length === 1) {
			return this.divRemSmall(n._s * n._d[0]);
		}

		// Test for easy cases -- |n1| <= |n2|
		switch (this.compareAbs(n)) {
			case 0: // n1 == n2
				return [this._s === n._s ? ONE : M_ONE, ZERO];
			case -1: // |n1| < |n2|
				return [ZERO, this];
		}

		var sign = this._s * n._s;
		var a = n.abs();
		var b_digits = this._d;
		var b_index = b_digits.length;
		var digits = n._d.length;
		var quot = [];
		var guess;

		var part = new BigInteger([], 0, CONSTRUCT);

		while (b_index) {
			part._d.unshift(b_digits[--b_index]);
			part = new BigInteger(part._d, 1, CONSTRUCT);

			if (part.compareAbs(n) < 0) {
				quot.push(0);
				continue;
			}
			if (part._s === 0) {
				guess = 0;
			}
			else {
				var xlen = part._d.length, ylen = a._d.length;
				var highx = part._d[xlen-1]*BigInteger_base + part._d[xlen-2];
				var highy = a._d[ylen-1]*BigInteger_base + a._d[ylen-2];
				if (part._d.length > a._d.length) {
					// The length of part._d can either match a._d length,
					// or exceed it by one.
					highx = (highx+1)*BigInteger_base;
				}
				guess = Math.ceil(highx/highy);
			}
			do {
				var check = a.multiplySingleDigit(guess);
				if (check.compareAbs(part) <= 0) {
					break;
				}
				guess--;
			} while (guess);

			quot.push(guess);
			if (!guess) {
				continue;
			}
			var diff = part.subtract(check);
			part._d = diff._d.slice();
		}

		return [new BigInteger(quot.reverse(), sign, CONSTRUCT),
			new BigInteger(part._d, this._s, CONSTRUCT)];
	};

// Throws an exception if n is outside of (-BigInteger.base, -1] or
// [1, BigInteger.base).  It's not necessary to call this, since the
// other division functions will call it if they are able to.
	BigInteger.prototype.divRemSmall = function(n) {
		var r;
		n = +n;
		if (n === 0) {
			throw new Error("Divide by zero");
		}

		var n_s = n < 0 ? -1 : 1;
		var sign = this._s * n_s;
		n = Math.abs(n);

		if (n < 1 || n >= BigInteger_base) {
			throw new Error("Argument out of range");
		}

		if (this._s === 0) {
			return [ZERO, ZERO];
		}

		if (n === 1 || n === -1) {
			return [(sign === 1) ? this.abs() : new BigInteger(this._d, sign, CONSTRUCT), ZERO];
		}

		// 2 <= n < BigInteger_base

		// divide a single digit by a single digit
		if (this._d.length === 1) {
			var q = new BigInteger([(this._d[0] / n) | 0], 1, CONSTRUCT);
			r = new BigInteger([(this._d[0] % n) | 0], 1, CONSTRUCT);
			if (sign < 0) {
				q = q.negate();
			}
			if (this._s < 0) {
				r = r.negate();
			}
			return [q, r];
		}

		var digits = this._d.slice();
		var quot = new Array(digits.length);
		var part = 0;
		var diff = 0;
		var i = 0;
		var guess;

		while (digits.length) {
			part = part * BigInteger_base + digits[digits.length - 1];
			if (part < n) {
				quot[i++] = 0;
				digits.pop();
				diff = BigInteger_base * diff + part;
				continue;
			}
			if (part === 0) {
				guess = 0;
			}
			else {
				guess = (part / n) | 0;
			}

			var check = n * guess;
			diff = part - check;
			quot[i++] = guess;
			if (!guess) {
				digits.pop();
				continue;
			}

			digits.pop();
			part = diff;
		}

		r = new BigInteger([diff], 1, CONSTRUCT);
		if (this._s < 0) {
			r = r.negate();
		}
		return [new BigInteger(quot.reverse(), sign, CONSTRUCT), r];
	};

	/*
	 Function: isEven
	 Return true iff *this* is divisible by two.

	 Note that <BigInteger.ZERO> is even.

	 Returns:

	 true if *this* is even, false otherwise.

	 See Also:

	 <isOdd>
	 */
	BigInteger.prototype.isEven = function() {
		var digits = this._d;
		return this._s === 0 || digits.length === 0 || (digits[0] % 2) === 0;
	};

	/*
	 Function: isOdd
	 Return true iff *this* is not divisible by two.

	 Returns:

	 true if *this* is odd, false otherwise.

	 See Also:

	 <isEven>
	 */
	BigInteger.prototype.isOdd = function() {
		return !this.isEven();
	};

	/*
	 Function: sign
	 Get the sign of a <BigInteger>.

	 Returns:

	 * -1 if *this* < 0
	 * 0 if *this* == 0
	 * +1 if *this* > 0

	 See Also:

	 <isZero>, <isPositive>, <isNegative>, <compare>, <BigInteger.ZERO>
	 */
	BigInteger.prototype.sign = function() {
		return this._s;
	};

	/*
	 Function: isPositive
	 Return true iff *this* > 0.

	 Returns:

	 true if *this*.compare(<BigInteger.ZERO>) == 1.

	 See Also:

	 <sign>, <isZero>, <isNegative>, <isUnit>, <compare>, <BigInteger.ZERO>
	 */
	BigInteger.prototype.isPositive = function() {
		return this._s > 0;
	};

	/*
	 Function: isNegative
	 Return true iff *this* < 0.

	 Returns:

	 true if *this*.compare(<BigInteger.ZERO>) == -1.

	 See Also:

	 <sign>, <isPositive>, <isZero>, <isUnit>, <compare>, <BigInteger.ZERO>
	 */
	BigInteger.prototype.isNegative = function() {
		return this._s < 0;
	};

	/*
	 Function: isZero
	 Return true iff *this* == 0.

	 Returns:

	 true if *this*.compare(<BigInteger.ZERO>) == 0.

	 See Also:

	 <sign>, <isPositive>, <isNegative>, <isUnit>, <BigInteger.ZERO>
	 */
	BigInteger.prototype.isZero = function() {
		return this._s === 0;
	};

	/*
	 Function: exp10
	 Multiply a <BigInteger> by a power of 10.

	 This is equivalent to, but faster than

	 > if (n >= 0) {
	 >     return this.multiply(BigInteger("1e" + n));
	 > }
	 > else { // n <= 0
	 >     return this.quotient(BigInteger("1e" + -n));
	 > }

	 Parameters:

	 n - The power of 10 to multiply *this* by. *n* is converted to a
	 javascipt number and must be no greater than <BigInteger.MAX_EXP>
	 (0x7FFFFFFF), or an exception will be thrown.

	 Returns:

	 *this* * (10 ** *n*), truncated to an integer if necessary.

	 See Also:

	 <pow>, <multiply>
	 */
	BigInteger.prototype.exp10 = function(n) {
		n = +n;
		if (n === 0) {
			return this;
		}
		if (Math.abs(n) > Number(MAX_EXP)) {
			throw new Error("exponent too large in BigInteger.exp10");
		}
		// Optimization for this == 0. This also keeps us from having to trim zeros in the positive n case
		if (this._s === 0) {
			return ZERO;
		}
		if (n > 0) {
			var k = new BigInteger(this._d.slice(), this._s, CONSTRUCT);

			for (; n >= BigInteger_base_log10; n -= BigInteger_base_log10) {
				k._d.unshift(0);
			}
			if (n == 0)
				return k;
			k._s = 1;
			k = k.multiplySingleDigit(Math.pow(10, n));
			return (this._s < 0 ? k.negate() : k);
		} else if (-n >= this._d.length*BigInteger_base_log10) {
			return ZERO;
		} else {
			var k = new BigInteger(this._d.slice(), this._s, CONSTRUCT);

			for (n = -n; n >= BigInteger_base_log10; n -= BigInteger_base_log10) {
				k._d.shift();
			}
			return (n == 0) ? k : k.divRemSmall(Math.pow(10, n))[0];
		}
	};

	/*
	 Function: pow
	 Raise a <BigInteger> to a power.

	 In this implementation, 0**0 is 1.

	 Parameters:

	 n - The exponent to raise *this* by. *n* must be no greater than
	 <BigInteger.MAX_EXP> (0x7FFFFFFF), or an exception will be thrown.

	 Returns:

	 *this* raised to the *nth* power.

	 See Also:

	 <modPow>
	 */
	BigInteger.prototype.pow = function(n) {
		if (this.isUnit()) {
			if (this._s > 0) {
				return this;
			}
			else {
				return BigInteger(n).isOdd() ? this : this.negate();
			}
		}

		n = BigInteger(n);
		if (n._s === 0) {
			return ONE;
		}
		else if (n._s < 0) {
			if (this._s === 0) {
				throw new Error("Divide by zero");
			}
			else {
				return ZERO;
			}
		}
		if (this._s === 0) {
			return ZERO;
		}
		if (n.isUnit()) {
			return this;
		}

		if (n.compareAbs(MAX_EXP) > 0) {
			throw new Error("exponent too large in BigInteger.pow");
		}
		var x = this;
		var aux = ONE;
		var two = BigInteger.small[2];

		while (n.isPositive()) {
			if (n.isOdd()) {
				aux = aux.multiply(x);
				if (n.isUnit()) {
					return aux;
				}
			}
			x = x.square();
			n = n.quotient(two);
		}

		return aux;
	};

	/*
	 Function: modPow
	 Raise a <BigInteger> to a power (mod m).

	 Because it is reduced by a modulus, <modPow> is not limited by
	 <BigInteger.MAX_EXP> like <pow>.

	 Parameters:

	 exponent - The exponent to raise *this* by. Must be positive.
	 modulus - The modulus.

	 Returns:

	 *this* ^ *exponent* (mod *modulus*).

	 See Also:

	 <pow>, <mod>
	 */
	BigInteger.prototype.modPow = function(exponent, modulus) {
		var result = ONE;
		var base = this;

		while (exponent.isPositive()) {
			if (exponent.isOdd()) {
				result = result.multiply(base).remainder(modulus);
			}

			exponent = exponent.quotient(BigInteger.small[2]);
			if (exponent.isPositive()) {
				base = base.square().remainder(modulus);
			}
		}

		return result;
	};

	/*
	 Function: log
	 Get the natural logarithm of a <BigInteger> as a native JavaScript number.

	 This is equivalent to

	 > Math.log(this.toJSValue())

	 but handles values outside of the native number range.

	 Returns:

	 log( *this* )

	 See Also:

	 <toJSValue>
	 */
	BigInteger.prototype.log = function() {
		switch (this._s) {
			case 0:	 return -Infinity;
			case -1: return NaN;
			default: // Fall through.
		}

		var l = this._d.length;

		if (l*BigInteger_base_log10 < 30) {
			return Math.log(this.valueOf());
		}

		var N = Math.ceil(30/BigInteger_base_log10);
		var firstNdigits = this._d.slice(l - N);
		return Math.log((new BigInteger(firstNdigits, 1, CONSTRUCT)).valueOf()) + (l - N) * Math.log(BigInteger_base);
	};

	/*
	 Function: valueOf
	 Convert a <BigInteger> to a native JavaScript integer.

	 This is called automatically by JavaScipt to convert a <BigInteger> to a
	 native value.

	 Returns:

	 > parseInt(this.toString(), 10)

	 See Also:

	 <toString>, <toJSValue>
	 */
	BigInteger.prototype.valueOf = function() {
		return parseInt(this.toString(), 10);
	};

	/*
	 Function: toJSValue
	 Convert a <BigInteger> to a native JavaScript integer.

	 This is the same as valueOf, but more explicitly named.

	 Returns:

	 > parseInt(this.toString(), 10)

	 See Also:

	 <toString>, <valueOf>
	 */
	BigInteger.prototype.toJSValue = function() {
		return parseInt(this.toString(), 10);
	};

	var MAX_EXP = BigInteger(0x7FFFFFFF);
// Constant: MAX_EXP
// The largest exponent allowed in <pow> and <exp10> (0x7FFFFFFF or 2147483647).
	BigInteger.MAX_EXP = MAX_EXP;

	(function() {
		function makeUnary(fn) {
			return function(a) {
				return fn.call(BigInteger(a));
			};
		}

		function makeBinary(fn) {
			return function(a, b) {
				return fn.call(BigInteger(a), BigInteger(b));
			};
		}

		function makeTrinary(fn) {
			return function(a, b, c) {
				return fn.call(BigInteger(a), BigInteger(b), BigInteger(c));
			};
		}

		(function() {
			var i, fn;
			var unary = "toJSValue,isEven,isOdd,sign,isZero,isNegative,abs,isUnit,square,negate,isPositive,toString,next,prev,log".split(",");
			var binary = "compare,remainder,divRem,subtract,add,quotient,divide,multiply,pow,compareAbs".split(",");
			var trinary = ["modPow"];

			for (i = 0; i < unary.length; i++) {
				fn = unary[i];
				BigInteger[fn] = makeUnary(BigInteger.prototype[fn]);
			}

			for (i = 0; i < binary.length; i++) {
				fn = binary[i];
				BigInteger[fn] = makeBinary(BigInteger.prototype[fn]);
			}

			for (i = 0; i < trinary.length; i++) {
				fn = trinary[i];
				BigInteger[fn] = makeTrinary(BigInteger.prototype[fn]);
			}

			BigInteger.exp10 = function(x, n) {
				return BigInteger(x).exp10(n);
			};
		})();
	})();

	exports.BigInteger = BigInteger;
})(typeof exports !== 'undefined' ? exports : this);

/*******************************************************************************
 * Copyright (c) 2013 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *    Andrew Banks - initial API and implementation and initial documentation
 *******************************************************************************/


// Only expose a single object name in the global namespace.
// Everything must go through this module. Global Paho.MQTT module
// only has a single public function, client, which returns
// a Paho.MQTT client object given connection details.
 
/**
 * Send and receive messages using web browsers.
 * <p> 
 * This programming interface lets a JavaScript client application use the MQTT V3.1 or
 * V3.1.1 protocol to connect to an MQTT-supporting messaging server.
 *  
 * The function supported includes:
 * <ol>
 * <li>Connecting to and disconnecting from a server. The server is identified by its host name and port number. 
 * <li>Specifying options that relate to the communications link with the server, 
 * for example the frequency of keep-alive heartbeats, and whether SSL/TLS is required.
 * <li>Subscribing to and receiving messages from MQTT Topics.
 * <li>Publishing messages to MQTT Topics.
 * </ol>
 * <p>
 * The API consists of two main objects:
 * <dl>
 * <dt><b>{@link Paho.MQTT.Client}</b></dt>
 * <dd>This contains methods that provide the functionality of the API,
 * including provision of callbacks that notify the application when a message
 * arrives from or is delivered to the messaging server,
 * or when the status of its connection to the messaging server changes.</dd>
 * <dt><b>{@link Paho.MQTT.Message}</b></dt>
 * <dd>This encapsulates the payload of the message along with various attributes
 * associated with its delivery, in particular the destination to which it has
 * been (or is about to be) sent.</dd>
 * </dl> 
 * <p>
 * The programming interface validates parameters passed to it, and will throw
 * an Error containing an error message intended for developer use, if it detects
 * an error with any parameter.
 * <p>
 * Example:
 * 
 * <code><pre>
client = new Paho.MQTT.Client(location.hostname, Number(location.port), "clientId");
client.onConnectionLost = onConnectionLost;
client.onMessageArrived = onMessageArrived;
client.connect({onSuccess:onConnect});

function onConnect() {
  // Once a connection has been made, make a subscription and send a message.
  console.log("onConnect");
  client.subscribe("/World");
  message = new Paho.MQTT.Message("Hello");
  message.destinationName = "/World";
  client.send(message); 
};
function onConnectionLost(responseObject) {
  if (responseObject.errorCode !== 0)
	console.log("onConnectionLost:"+responseObject.errorMessage);
};
function onMessageArrived(message) {
  console.log("onMessageArrived:"+message.payloadString);
  client.disconnect(); 
};	
 * </pre></code>
 * @namespace Paho.MQTT 
 */

if (typeof Paho === "undefined") {
	Paho = {};
}

Paho.MQTT = (function (global) {

	// Private variables below, these are only visible inside the function closure
	// which is used to define the module. 

	var version = "@VERSION@";
	var buildLevel = "@BUILDLEVEL@";
	
	/** 
	 * Unique message type identifiers, with associated
	 * associated integer values.
	 * @private 
	 */
	var MESSAGE_TYPE = {
		CONNECT: 1, 
		CONNACK: 2, 
		PUBLISH: 3,
		PUBACK: 4,
		PUBREC: 5, 
		PUBREL: 6,
		PUBCOMP: 7,
		SUBSCRIBE: 8,
		SUBACK: 9,
		UNSUBSCRIBE: 10,
		UNSUBACK: 11,
		PINGREQ: 12,
		PINGRESP: 13,
		DISCONNECT: 14
	};
	
	// Collection of utility methods used to simplify module code 
	// and promote the DRY pattern.  

	/**
	 * Validate an object's parameter names to ensure they 
	 * match a list of expected variables name for this option
	 * type. Used to ensure option object passed into the API don't
	 * contain erroneous parameters.
	 * @param {Object} obj - User options object
	 * @param {Object} keys - valid keys and types that may exist in obj. 
	 * @throws {Error} Invalid option parameter found. 
	 * @private 
	 */
	var validate = function(obj, keys) {
		for (var key in obj) {
			if (obj.hasOwnProperty(key)) {       		
				if (keys.hasOwnProperty(key)) {
					if (typeof obj[key] !== keys[key])
					   throw new Error(format(ERROR.INVALID_TYPE, [typeof obj[key], key]));
				} else {	
					var errorStr = "Unknown property, " + key + ". Valid properties are:";
					for (var key in keys)
						if (keys.hasOwnProperty(key))
							errorStr = errorStr+" "+key;
					throw new Error(errorStr);
				}
			}
		}
	};

	/**
	 * Return a new function which runs the user function bound
	 * to a fixed scope. 
	 * @param {function} User function
	 * @param {object} Function scope  
	 * @return {function} User function bound to another scope
	 * @private 
	 */
	var scope = function (f, scope) {
		return function () {
			return f.apply(scope, arguments);
		};
	};
	
	/** 
	 * Unique message type identifiers, with associated
	 * associated integer values.
	 * @private 
	 */
	var ERROR = {
		OK: {code:0, text:"AMQJSC0000I OK."},
		CONNECT_TIMEOUT: {code:1, text:"AMQJSC0001E Connect timed out."},
		SUBSCRIBE_TIMEOUT: {code:2, text:"AMQJS0002E Subscribe timed out."}, 
		UNSUBSCRIBE_TIMEOUT: {code:3, text:"AMQJS0003E Unsubscribe timed out."},
		PING_TIMEOUT: {code:4, text:"AMQJS0004E Ping timed out."},
		INTERNAL_ERROR: {code:5, text:"AMQJS0005E Internal error. Error Message: {0}, Stack trace: {1}"},
		CONNACK_RETURNCODE: {code:6, text:"AMQJS0006E Bad Connack return code:{0} {1}."},
		SOCKET_ERROR: {code:7, text:"AMQJS0007E Socket error:{0}."},
		SOCKET_CLOSE: {code:8, text:"AMQJS0008I Socket closed."},
		MALFORMED_UTF: {code:9, text:"AMQJS0009E Malformed UTF data:{0} {1} {2}."},
		UNSUPPORTED: {code:10, text:"AMQJS0010E {0} is not supported by this browser."},
		INVALID_STATE: {code:11, text:"AMQJS0011E Invalid state {0}."},
		INVALID_TYPE: {code:12, text:"AMQJS0012E Invalid type {0} for {1}."},
		INVALID_ARGUMENT: {code:13, text:"AMQJS0013E Invalid argument {0} for {1}."},
		UNSUPPORTED_OPERATION: {code:14, text:"AMQJS0014E Unsupported operation."},
		INVALID_STORED_DATA: {code:15, text:"AMQJS0015E Invalid data in local storage key={0} value={1}."},
		INVALID_MQTT_MESSAGE_TYPE: {code:16, text:"AMQJS0016E Invalid MQTT message type {0}."},
		MALFORMED_UNICODE: {code:17, text:"AMQJS0017E Malformed Unicode string:{0} {1}."},
	};
	
	/** CONNACK RC Meaning. */
	var CONNACK_RC = {
		0:"Connection Accepted",
		1:"Connection Refused: unacceptable protocol version",
		2:"Connection Refused: identifier rejected",
		3:"Connection Refused: server unavailable",
		4:"Connection Refused: bad user name or password",
		5:"Connection Refused: not authorized"
	};

	/**
	 * Format an error message text.
	 * @private
	 * @param {error} ERROR.KEY value above.
	 * @param {substitutions} [array] substituted into the text.
	 * @return the text with the substitutions made.
	 */
	var format = function(error, substitutions) {
		var text = error.text;
		if (substitutions) {
		  var field,start;
		  for (var i=0; i<substitutions.length; i++) {
			field = "{"+i+"}";
			start = text.indexOf(field);
			if(start > 0) {
				var part1 = text.substring(0,start);
				var part2 = text.substring(start+field.length);
				text = part1+substitutions[i]+part2;
			}
		  }
		}
		return text;
	};
	
	//MQTT protocol and version          6    M    Q    I    s    d    p    3
	var MqttProtoIdentifierv3 = [0x00,0x06,0x4d,0x51,0x49,0x73,0x64,0x70,0x03];
	//MQTT proto/version for 311         4    M    Q    T    T    4
	var MqttProtoIdentifierv4 = [0x00,0x04,0x4d,0x51,0x54,0x54,0x04];
	
	/**
	 * Construct an MQTT wire protocol message.
	 * @param type MQTT packet type.
	 * @param options optional wire message attributes.
	 * 
	 * Optional properties
	 * 
	 * messageIdentifier: message ID in the range [0..65535]
	 * payloadMessage:	Application Message - PUBLISH only
	 * connectStrings:	array of 0 or more Strings to be put into the CONNECT payload
	 * topics:			array of strings (SUBSCRIBE, UNSUBSCRIBE)
	 * requestQoS:		array of QoS values [0..2]
	 *  
	 * "Flag" properties 
	 * cleanSession:	true if present / false if absent (CONNECT)
	 * willMessage:  	true if present / false if absent (CONNECT)
	 * isRetained:		true if present / false if absent (CONNECT)
	 * userName:		true if present / false if absent (CONNECT)
	 * password:		true if present / false if absent (CONNECT)
	 * keepAliveInterval:	integer [0..65535]  (CONNECT)
	 *
	 * @private
	 * @ignore
	 */
	var WireMessage = function (type, options) { 	
		this.type = type;
		for (var name in options) {
			if (options.hasOwnProperty(name)) {
				this[name] = options[name];
			}
		}
	};
	
	WireMessage.prototype.encode = function() {
		// Compute the first byte of the fixed header
		var first = ((this.type & 0x0f) << 4);
		
		/*
		 * Now calculate the length of the variable header + payload by adding up the lengths
		 * of all the component parts
		 */

		var remLength = 0;
		var topicStrLength = new Array();
		var destinationNameLength = 0;
		
		// if the message contains a messageIdentifier then we need two bytes for that
		if (this.messageIdentifier != undefined)
			remLength += 2;

		switch(this.type) {
			// If this a Connect then we need to include 12 bytes for its header
			case MESSAGE_TYPE.CONNECT:
				switch(this.mqttVersion) {
					case 3:
						remLength += MqttProtoIdentifierv3.length + 3;
						break;
					case 4:
						remLength += MqttProtoIdentifierv4.length + 3;
						break;
				}

				remLength += UTF8Length(this.clientId) + 2;
				if (this.willMessage != undefined) {
					remLength += UTF8Length(this.willMessage.destinationName) + 2;
					// Will message is always a string, sent as UTF-8 characters with a preceding length.
					var willMessagePayloadBytes = this.willMessage.payloadBytes;
					if (!(willMessagePayloadBytes instanceof Uint8Array))
						willMessagePayloadBytes = new Uint8Array(payloadBytes);
					remLength += willMessagePayloadBytes.byteLength +2;
				}
				if (this.userName != undefined)
					remLength += UTF8Length(this.userName) + 2;	
				if (this.password != undefined)
					remLength += UTF8Length(this.password) + 2;
			break;

			// Subscribe, Unsubscribe can both contain topic strings
			case MESSAGE_TYPE.SUBSCRIBE:	        	
				first |= 0x02; // Qos = 1;
				for ( var i = 0; i < this.topics.length; i++) {
					topicStrLength[i] = UTF8Length(this.topics[i]);
					remLength += topicStrLength[i] + 2;
				}
				remLength += this.requestedQos.length; // 1 byte for each topic's Qos
				// QoS on Subscribe only
				break;

			case MESSAGE_TYPE.UNSUBSCRIBE:
				first |= 0x02; // Qos = 1;
				for ( var i = 0; i < this.topics.length; i++) {
					topicStrLength[i] = UTF8Length(this.topics[i]);
					remLength += topicStrLength[i] + 2;
				}
				break;

			case MESSAGE_TYPE.PUBREL:
				first |= 0x02; // Qos = 1;
				break;

			case MESSAGE_TYPE.PUBLISH:
				if (this.payloadMessage.duplicate) first |= 0x08;
				first  = first |= (this.payloadMessage.qos << 1);
				if (this.payloadMessage.retained) first |= 0x01;
				destinationNameLength = UTF8Length(this.payloadMessage.destinationName);
				remLength += destinationNameLength + 2;	   
				var payloadBytes = this.payloadMessage.payloadBytes;
				remLength += payloadBytes.byteLength;  
				if (payloadBytes instanceof ArrayBuffer)
					payloadBytes = new Uint8Array(payloadBytes);
				else if (!(payloadBytes instanceof Uint8Array))
					payloadBytes = new Uint8Array(payloadBytes.buffer);
				break;

			case MESSAGE_TYPE.DISCONNECT:
				break;

			default:
				;
		}

		// Now we can allocate a buffer for the message

		var mbi = encodeMBI(remLength);  // Convert the length to MQTT MBI format
		var pos = mbi.length + 1;        // Offset of start of variable header
		var buffer = new ArrayBuffer(remLength + pos);
		var byteStream = new Uint8Array(buffer);    // view it as a sequence of bytes

		//Write the fixed header into the buffer
		byteStream[0] = first;
		byteStream.set(mbi,1);

		// If this is a PUBLISH then the variable header starts with a topic
		if (this.type == MESSAGE_TYPE.PUBLISH)
			pos = writeString(this.payloadMessage.destinationName, destinationNameLength, byteStream, pos);
		// If this is a CONNECT then the variable header contains the protocol name/version, flags and keepalive time
		
		else if (this.type == MESSAGE_TYPE.CONNECT) {
			switch (this.mqttVersion) {
				case 3:
					byteStream.set(MqttProtoIdentifierv3, pos);
					pos += MqttProtoIdentifierv3.length;
					break;
				case 4:
					byteStream.set(MqttProtoIdentifierv4, pos);
					pos += MqttProtoIdentifierv4.length;
					break;
			}
			var connectFlags = 0;
			if (this.cleanSession) 
				connectFlags = 0x02;
			if (this.willMessage != undefined ) {
				connectFlags |= 0x04;
				connectFlags |= (this.willMessage.qos<<3);
				if (this.willMessage.retained) {
					connectFlags |= 0x20;
				}
			}
			if (this.userName != undefined)
				connectFlags |= 0x80;
			if (this.password != undefined)
				connectFlags |= 0x40;
			byteStream[pos++] = connectFlags; 
			pos = writeUint16 (this.keepAliveInterval, byteStream, pos);
		}

		// Output the messageIdentifier - if there is one
		if (this.messageIdentifier != undefined)
			pos = writeUint16 (this.messageIdentifier, byteStream, pos);

		switch(this.type) {
			case MESSAGE_TYPE.CONNECT:
				pos = writeString(this.clientId, UTF8Length(this.clientId), byteStream, pos); 
				if (this.willMessage != undefined) {
					pos = writeString(this.willMessage.destinationName, UTF8Length(this.willMessage.destinationName), byteStream, pos);
					pos = writeUint16(willMessagePayloadBytes.byteLength, byteStream, pos);
					byteStream.set(willMessagePayloadBytes, pos);
					pos += willMessagePayloadBytes.byteLength;
					
				}
			if (this.userName != undefined)
				pos = writeString(this.userName, UTF8Length(this.userName), byteStream, pos);
			if (this.password != undefined) 
				pos = writeString(this.password, UTF8Length(this.password), byteStream, pos);
			break;

			case MESSAGE_TYPE.PUBLISH:	
				// PUBLISH has a text or binary payload, if text do not add a 2 byte length field, just the UTF characters.	
				byteStream.set(payloadBytes, pos);
					
				break;

//    	    case MESSAGE_TYPE.PUBREC:	
//    	    case MESSAGE_TYPE.PUBREL:	
//    	    case MESSAGE_TYPE.PUBCOMP:	
//    	    	break;

			case MESSAGE_TYPE.SUBSCRIBE:
				// SUBSCRIBE has a list of topic strings and request QoS
				for (var i=0; i<this.topics.length; i++) {
					pos = writeString(this.topics[i], topicStrLength[i], byteStream, pos);
					byteStream[pos++] = this.requestedQos[i];
				}
				break;

			case MESSAGE_TYPE.UNSUBSCRIBE:	
				// UNSUBSCRIBE has a list of topic strings
				for (var i=0; i<this.topics.length; i++)
					pos = writeString(this.topics[i], topicStrLength[i], byteStream, pos);
				break;

			default:
				// Do nothing.
		}

		return buffer;
	}	

	function decodeMessage(input,pos) {
	    var startingPos = pos;
		var first = input[pos];
		var type = first >> 4;
		var messageInfo = first &= 0x0f;
		pos += 1;
		

		// Decode the remaining length (MBI format)

		var digit;
		var remLength = new BigInteger(0);
		var multiplier = 1;
		do {
			if (pos == input.length) {
			    return [null,startingPos];
			}
			digit = input[pos++];
			remLength = remLength.add(new BigInteger((digit & 0x7F) * multiplier));
			multiplier *= 128;
		} while ((digit & 0x80) != 0);

		var endPos = pos+parseInt(remLength.toString());
		if (endPos > input.length) {
		    return [null,startingPos];
		}

		var wireMessage = new WireMessage(type);
		switch(type) {
			case MESSAGE_TYPE.CONNACK:
				var connectAcknowledgeFlags = input[pos++];
				if (connectAcknowledgeFlags & 0x01)
					wireMessage.sessionPresent = true;
				wireMessage.returnCode = input[pos++];
				break;
			
			case MESSAGE_TYPE.PUBLISH:     	    	
				var qos = (messageInfo >> 1) & 0x03;
							
				var len = readUint16(input, pos);
				pos += 2;
				var topicName = parseUTF8(input, pos, len);
				pos += len;
				// If QoS 1 or 2 there will be a messageIdentifier
				if (qos > 0) {
					wireMessage.messageIdentifier = readUint16(input, pos);
					pos += 2;
				}
				
				var message = new Paho.MQTT.Message(input.subarray(pos, endPos));
				if ((messageInfo & 0x01) == 0x01) 
					message.retained = true;
				if ((messageInfo & 0x08) == 0x08)
					message.duplicate =  true;
				message.qos = qos;
				message.destinationName = topicName;
				wireMessage.payloadMessage = message;	
				break;
			
			case  MESSAGE_TYPE.PUBACK:
			case  MESSAGE_TYPE.PUBREC:	    
			case  MESSAGE_TYPE.PUBREL:    
			case  MESSAGE_TYPE.PUBCOMP:
			case  MESSAGE_TYPE.UNSUBACK:    	    	
				wireMessage.messageIdentifier = readUint16(input, pos);
				break;
				
			case  MESSAGE_TYPE.SUBACK:
				wireMessage.messageIdentifier = readUint16(input, pos);
				pos += 2;
				wireMessage.returnCode = input.subarray(pos, endPos);	
				break;
		
			default:
				;
		}
				
		return [wireMessage,endPos];	
	}

	function writeUint16(input, buffer, offset) {
		buffer[offset++] = input >> 8;      //MSB
		buffer[offset++] = input % 256;     //LSB 
		return offset;
	}	

	function writeString(input, utf8Length, buffer, offset) {
		offset = writeUint16(utf8Length, buffer, offset);
		stringToUTF8(input, buffer, offset);
		return offset + utf8Length;
	}	

	function readUint16(buffer, offset) {
		return 256*buffer[offset] + buffer[offset+1];
	}	

	/**
	 * Encodes an MQTT Multi-Byte Integer
	 * @private 
	 */
	function encodeMBI(number) {
		var output = new Array(1);
		var numBytes = 0;

		do {
			var digit = number % 128;
			number = number >> 7;
			if (number > 0) {
				digit |= 0x80;
			}
			output[numBytes++] = digit;
		} while ( (number > 0) && (numBytes<8) ); // protocol hack for bigger payloads

		return output;
	}

	/**
	 * Takes a String and calculates its length in bytes when encoded in UTF8.
	 * @private
	 */
	function UTF8Length(input) {
		var output = 0;
		for (var i = 0; i<input.length; i++) 
		{
			var charCode = input.charCodeAt(i);
				if (charCode > 0x7FF)
				   {
					  // Surrogate pair means its a 4 byte character
					  if (0xD800 <= charCode && charCode <= 0xDBFF)
						{
						  i++;
						  output++;
						}
				   output +=3;
				   }
			else if (charCode > 0x7F)
				output +=2;
			else
				output++;
		} 
		return output;
	}
	
	/**
	 * Takes a String and writes it into an array as UTF8 encoded bytes.
	 * @private
	 */
	function stringToUTF8(input, output, start) {
		var pos = start;
		for (var i = 0; i<input.length; i++) {
			var charCode = input.charCodeAt(i);
			
			// Check for a surrogate pair.
			if (0xD800 <= charCode && charCode <= 0xDBFF) {
				var lowCharCode = input.charCodeAt(++i);
				if (isNaN(lowCharCode)) {
					throw new Error(format(ERROR.MALFORMED_UNICODE, [charCode, lowCharCode]));
				}
				charCode = ((charCode - 0xD800)<<10) + (lowCharCode - 0xDC00) + 0x10000;
			
			}
			
			if (charCode <= 0x7F) {
				output[pos++] = charCode;
			} else if (charCode <= 0x7FF) {
				output[pos++] = charCode>>6  & 0x1F | 0xC0;
				output[pos++] = charCode     & 0x3F | 0x80;
			} else if (charCode <= 0xFFFF) {    				    
				output[pos++] = charCode>>12 & 0x0F | 0xE0;
				output[pos++] = charCode>>6  & 0x3F | 0x80;   
				output[pos++] = charCode     & 0x3F | 0x80;   
			} else {
				output[pos++] = charCode>>18 & 0x07 | 0xF0;
				output[pos++] = charCode>>12 & 0x3F | 0x80;
				output[pos++] = charCode>>6  & 0x3F | 0x80;
				output[pos++] = charCode     & 0x3F | 0x80;
			};
		} 
		return output;
	}
	
	function parseUTF8(input, offset, length) {
		var output = "";
		var utf16;
		var pos = offset;

		while (pos < offset+length)
		{
			var byte1 = input[pos++];
			if (byte1 < 128)
				utf16 = byte1;
			else 
			{
				var byte2 = input[pos++]-128;
				if (byte2 < 0) 
					throw new Error(format(ERROR.MALFORMED_UTF, [byte1.toString(16), byte2.toString(16),""]));
				if (byte1 < 0xE0)             // 2 byte character
					utf16 = 64*(byte1-0xC0) + byte2;
				else 
				{ 
					var byte3 = input[pos++]-128;
					if (byte3 < 0) 
						throw new Error(format(ERROR.MALFORMED_UTF, [byte1.toString(16), byte2.toString(16), byte3.toString(16)]));
					if (byte1 < 0xF0)        // 3 byte character
						utf16 = 4096*(byte1-0xE0) + 64*byte2 + byte3;
								else
								{
								   var byte4 = input[pos++]-128;
								   if (byte4 < 0) 
						throw new Error(format(ERROR.MALFORMED_UTF, [byte1.toString(16), byte2.toString(16), byte3.toString(16), byte4.toString(16)]));
								   if (byte1 < 0xF8)        // 4 byte character 
										   utf16 = 262144*(byte1-0xF0) + 4096*byte2 + 64*byte3 + byte4;
					   else                     // longer encodings are not supported  
						throw new Error(format(ERROR.MALFORMED_UTF, [byte1.toString(16), byte2.toString(16), byte3.toString(16), byte4.toString(16)]));
								}
				}
			}  

				if (utf16 > 0xFFFF)   // 4 byte character - express as a surrogate pair
				  {
					 utf16 -= 0x10000;
					 output += String.fromCharCode(0xD800 + (utf16 >> 10)); // lead character
					 utf16 = 0xDC00 + (utf16 & 0x3FF);  // trail character
				  }
			output += String.fromCharCode(utf16);
		}
		return output;
	}
	
	/** 
	 * Repeat keepalive requests, monitor responses.
	 * @ignore
	 */
	var Pinger = function(client, window, keepAliveInterval) { 
		this._client = client;        	
		this._window = window;
		this._keepAliveInterval = keepAliveInterval*1000;     	
		this.isReset = false;
		
		var pingReq = new WireMessage(MESSAGE_TYPE.PINGREQ).encode(); 
		
		var doTimeout = function (pinger) {
			return function () {
				return doPing.apply(pinger);
			};
		};
		
		/** @ignore */
		var doPing = function() { 
			if (!this.isReset) {
				this._client._trace("Pinger.doPing", "Timed out");
				this._client._disconnected( ERROR.PING_TIMEOUT.code , format(ERROR.PING_TIMEOUT));
			} else {
				this.isReset = false;
				this._client._trace("Pinger.doPing", "send PINGREQ");
				this._client.socket.send(pingReq); 
				this.timeout = this._window.setTimeout(doTimeout(this), this._keepAliveInterval);
			}
		}

		this.reset = function() {
			this.isReset = true;
			this._window.clearTimeout(this.timeout);
			if (this._keepAliveInterval > 0)
				this.timeout = setTimeout(doTimeout(this), this._keepAliveInterval);
		}

		this.cancel = function() {
			this._window.clearTimeout(this.timeout);
		}
	 }; 

	/**
	 * Monitor request completion.
	 * @ignore
	 */
	var Timeout = function(client, window, timeoutSeconds, action, args) {
		this._window = window;
		if (!timeoutSeconds)
			timeoutSeconds = 30;
		
		var doTimeout = function (action, client, args) {
			return function () {
				return action.apply(client, args);
			};
		};
		this.timeout = setTimeout(doTimeout(action, client, args), timeoutSeconds * 1000);
		
		this.cancel = function() {
			this._window.clearTimeout(this.timeout);
		}
	}; 
	
	/**
	 * Internal implementation of the Websockets MQTT V3.1 client.
	 * 
	 * @name Paho.MQTT.ClientImpl @constructor 
	 * @param {String} host the DNS nameof the webSocket host. 
	 * @param {Number} port the port number for that host.
	 * @param {String} clientId the MQ client identifier.
	 */
	var ClientImpl = function (uri, host, port, path, clientId) {
		// Check dependencies are satisfied in this browser.
		if (!("WebSocket" in global && global["WebSocket"] !== null)) {
			throw new Error(format(ERROR.UNSUPPORTED, ["WebSocket"]));
		}
		if (!("localStorage" in global && global["localStorage"] !== null)) {
			throw new Error(format(ERROR.UNSUPPORTED, ["localStorage"]));
		}
		if (!("ArrayBuffer" in global && global["ArrayBuffer"] !== null)) {
			throw new Error(format(ERROR.UNSUPPORTED, ["ArrayBuffer"]));
		}
		this._trace("Paho.MQTT.Client", uri, host, port, path, clientId);

		this.host = host;
		this.port = port;
		this.path = path;
		this.uri = uri;
		this.clientId = clientId;

		// Local storagekeys are qualified with the following string.
		// The conditional inclusion of path in the key is for backward
		// compatibility to when the path was not configurable and assumed to
		// be /mqtt
		this._localKey=host+":"+port+(path!="/mqtt"?":"+path:"")+":"+clientId+":";

		// Create private instance-only message queue
		// Internal queue of messages to be sent, in sending order. 
		this._msg_queue = [];

		// Messages we have sent and are expecting a response for, indexed by their respective message ids. 
		this._sentMessages = {};

		// Messages we have received and acknowleged and are expecting a confirm message for
		// indexed by their respective message ids. 
		this._receivedMessages = {};

		// Internal list of callbacks to be executed when messages
		// have been successfully sent over web socket, e.g. disconnect
		// when it doesn't have to wait for ACK, just message is dispatched.
		this._notify_msg_sent = {};

		// Unique identifier for SEND messages, incrementing
		// counter as messages are sent.
		this._message_identifier = 1;
		
		// Used to determine the transmission sequence of stored sent messages.
		this._sequence = 0;
		

		// Load the local state, if any, from the saved version, only restore state relevant to this client.   	
		for (var key in localStorage)
			if (   key.indexOf("Sent:"+this._localKey) == 0  		    
				|| key.indexOf("Received:"+this._localKey) == 0)
			this.restore(key);
	};

	// Messaging Client public instance members. 
	ClientImpl.prototype.host;
	ClientImpl.prototype.port;
	ClientImpl.prototype.path;
	ClientImpl.prototype.uri;
	ClientImpl.prototype.clientId;

	// Messaging Client private instance members.
	ClientImpl.prototype.socket;
	/* true once we have received an acknowledgement to a CONNECT packet. */
	ClientImpl.prototype.connected = false;
	/* The largest message identifier allowed, may not be larger than 2**16 but 
	 * if set smaller reduces the maximum number of outbound messages allowed.
	 */ 
	ClientImpl.prototype.maxMessageIdentifier = 65536;
	ClientImpl.prototype.connectOptions;
	ClientImpl.prototype.hostIndex;
	ClientImpl.prototype.onConnectionLost;
	ClientImpl.prototype.onMessageDelivered;
	ClientImpl.prototype.onMessageArrived;
	ClientImpl.prototype.traceFunction;
	ClientImpl.prototype._msg_queue = null;
	ClientImpl.prototype._connectTimeout;
	/* The sendPinger monitors how long we allow before we send data to prove to the server that we are alive. */
	ClientImpl.prototype.sendPinger = null;
	/* The receivePinger monitors how long we allow before we require evidence that the server is alive. */
	ClientImpl.prototype.receivePinger = null;
	
	ClientImpl.prototype.receiveBuffer = null;
	
	ClientImpl.prototype._traceBuffer = null;
	ClientImpl.prototype._MAX_TRACE_ENTRIES = 100;

	ClientImpl.prototype.connect = function (connectOptions) {
		var connectOptionsMasked = this._traceMask(connectOptions, "password"); 
		this._trace("Client.connect", connectOptionsMasked, this.socket, this.connected);
		
		if (this.connected) 
			throw new Error(format(ERROR.INVALID_STATE, ["already connected"]));
		if (this.socket)
			throw new Error(format(ERROR.INVALID_STATE, ["already connected"]));
		
		this.connectOptions = connectOptions;
		
		if (connectOptions.uris) {
			this.hostIndex = 0;
			this._doConnect(connectOptions.uris[0]);  
		} else {
			this._doConnect(this.uri);  		
		}
		
	};

	ClientImpl.prototype.subscribe = function (filter, subscribeOptions) {
		this._trace("Client.subscribe", filter, subscribeOptions);
			  
		if (!this.connected)
			throw new Error(format(ERROR.INVALID_STATE, ["not connected"]));
		
		var wireMessage = new WireMessage(MESSAGE_TYPE.SUBSCRIBE);
		wireMessage.topics=[filter];
		if (subscribeOptions.qos != undefined)
			wireMessage.requestedQos = [subscribeOptions.qos];
		else 
			wireMessage.requestedQos = [0];
		
		if (subscribeOptions.onSuccess) {
			wireMessage.onSuccess = function(grantedQos) {subscribeOptions.onSuccess({invocationContext:subscribeOptions.invocationContext,grantedQos:grantedQos});};
		}

		if (subscribeOptions.onFailure) {
			wireMessage.onFailure = function(errorCode) {subscribeOptions.onFailure({invocationContext:subscribeOptions.invocationContext,errorCode:errorCode});};
		}

		if (subscribeOptions.timeout) {
			wireMessage.timeOut = new Timeout(this, window, subscribeOptions.timeout, subscribeOptions.onFailure
					, [{invocationContext:subscribeOptions.invocationContext, 
						errorCode:ERROR.SUBSCRIBE_TIMEOUT.code, 
						errorMessage:format(ERROR.SUBSCRIBE_TIMEOUT)}]);
		}
		
		// All subscriptions return a SUBACK. 
		this._requires_ack(wireMessage);
		this._schedule_message(wireMessage);
	};

	/** @ignore */
	ClientImpl.prototype.unsubscribe = function(filter, unsubscribeOptions) {  
		this._trace("Client.unsubscribe", filter, unsubscribeOptions);
		
		if (!this.connected)
		   throw new Error(format(ERROR.INVALID_STATE, ["not connected"]));
		
		var wireMessage = new WireMessage(MESSAGE_TYPE.UNSUBSCRIBE);
		wireMessage.topics = [filter];
		
		if (unsubscribeOptions.onSuccess) {
			wireMessage.callback = function() {unsubscribeOptions.onSuccess({invocationContext:unsubscribeOptions.invocationContext});};
		}
		if (unsubscribeOptions.timeout) {
			wireMessage.timeOut = new Timeout(this, window, unsubscribeOptions.timeout, unsubscribeOptions.onFailure
					, [{invocationContext:unsubscribeOptions.invocationContext,
						errorCode:ERROR.UNSUBSCRIBE_TIMEOUT.code,
						errorMessage:format(ERROR.UNSUBSCRIBE_TIMEOUT)}]);
		}
	 
		// All unsubscribes return a SUBACK.         
		this._requires_ack(wireMessage);
		this._schedule_message(wireMessage);
	};
	 
	ClientImpl.prototype.send = function (message) {
		this._trace("Client.send", message);

		if (!this.connected)
		   throw new Error(format(ERROR.INVALID_STATE, ["not connected"]));
		
		wireMessage = new WireMessage(MESSAGE_TYPE.PUBLISH);
		wireMessage.payloadMessage = message;
		
		if (message.qos > 0)
			this._requires_ack(wireMessage);
		else if (this.onMessageDelivered)
			this._notify_msg_sent[wireMessage] = this.onMessageDelivered(wireMessage.payloadMessage);
		this._schedule_message(wireMessage);
	};
	
	ClientImpl.prototype.disconnect = function () {
		this._trace("Client.disconnect");

		if (!this.socket)
			throw new Error(format(ERROR.INVALID_STATE, ["not connecting or connected"]));
		
		wireMessage = new WireMessage(MESSAGE_TYPE.DISCONNECT);

		// Run the disconnected call back as soon as the message has been sent,
		// in case of a failure later on in the disconnect processing.
		// as a consequence, the _disconected call back may be run several times.
		this._notify_msg_sent[wireMessage] = scope(this._disconnected, this);

		this._schedule_message(wireMessage);
	};
	
	ClientImpl.prototype.getTraceLog = function () {
		if ( this._traceBuffer !== null ) {
			this._trace("Client.getTraceLog", new Date());
			this._trace("Client.getTraceLog in flight messages", this._sentMessages.length);
			for (var key in this._sentMessages)
				this._trace("_sentMessages ",key, this._sentMessages[key]);
			for (var key in this._receivedMessages)
				this._trace("_receivedMessages ",key, this._receivedMessages[key]);
			
			return this._traceBuffer;
		}
	};
	
	ClientImpl.prototype.startTrace = function () {
		if ( this._traceBuffer === null ) {
			this._traceBuffer = [];
		}
		this._trace("Client.startTrace", new Date(), version);
	};
	
	ClientImpl.prototype.stopTrace = function () {
		delete this._traceBuffer;
	};

	ClientImpl.prototype._doConnect = function (wsurl) { 	        
		// When the socket is open, this client will send the CONNECT WireMessage using the saved parameters. 
		if (this.connectOptions.useSSL) {
		    var uriParts = wsurl.split(":");
		    uriParts[0] = "wss";
		    wsurl = uriParts.join(":");
		}
		this.connected = false;
		/*if (this.connectOptions.mqttVersion < 4) {
			this.socket = new WebSocket(wsurl, ["mqttv3.1"]);
		} else {
			this.socket = new WebSocket(wsurl, ["mqtt"]);
		}*/
		this.socket = new WebSocket(wsurl);
		this.socket.binaryType = 'arraybuffer';
		
		this.socket.onopen = scope(this._on_socket_open, this);
		this.socket.onmessage = scope(this._on_socket_message, this);
		this.socket.onerror = scope(this._on_socket_error, this);
		this.socket.onclose = scope(this._on_socket_close, this);
		
		this.sendPinger = new Pinger(this, window, this.connectOptions.keepAliveInterval);
		this.receivePinger = new Pinger(this, window, this.connectOptions.keepAliveInterval);
		
		this._connectTimeout = new Timeout(this, window, this.connectOptions.timeout, this._disconnected,  [ERROR.CONNECT_TIMEOUT.code, format(ERROR.CONNECT_TIMEOUT)]);
	};

	
	// Schedule a new message to be sent over the WebSockets
	// connection. CONNECT messages cause WebSocket connection
	// to be started. All other messages are queued internally
	// until this has happened. When WS connection starts, process
	// all outstanding messages. 
	ClientImpl.prototype._schedule_message = function (message) {
		this._msg_queue.push(message);
		// Process outstanding messages in the queue if we have an  open socket, and have received CONNACK. 
		if (this.connected) {
			this._process_queue();
		}
	};

	ClientImpl.prototype.store = function(prefix, wireMessage) {
		var storedMessage = {type:wireMessage.type, messageIdentifier:wireMessage.messageIdentifier, version:1};
		
		switch(wireMessage.type) {
		  case MESSAGE_TYPE.PUBLISH:
			  if(wireMessage.pubRecReceived)
				  storedMessage.pubRecReceived = true;
			  
			  // Convert the payload to a hex string.
			  storedMessage.payloadMessage = {};
			  var hex = "";
			  var messageBytes = wireMessage.payloadMessage.payloadBytes;
			  for (var i=0; i<messageBytes.length; i++) {
				if (messageBytes[i] <= 0xF)
				  hex = hex+"0"+messageBytes[i].toString(16);
				else 
				  hex = hex+messageBytes[i].toString(16);
			  }
			  storedMessage.payloadMessage.payloadHex = hex;
			  
			  storedMessage.payloadMessage.qos = wireMessage.payloadMessage.qos;
			  storedMessage.payloadMessage.destinationName = wireMessage.payloadMessage.destinationName;
			  if (wireMessage.payloadMessage.duplicate) 
				  storedMessage.payloadMessage.duplicate = true;
			  if (wireMessage.payloadMessage.retained) 
				  storedMessage.payloadMessage.retained = true;	   
			  
			  // Add a sequence number to sent messages.
			  if ( prefix.indexOf("Sent:") == 0 ) {
				  if ( wireMessage.sequence === undefined )
					  wireMessage.sequence = ++this._sequence;
				  storedMessage.sequence = wireMessage.sequence;
			  }
			  break;    
			  
			default:
				throw Error(format(ERROR.INVALID_STORED_DATA, [key, storedMessage]));
		}
		localStorage.setItem(prefix+this._localKey+wireMessage.messageIdentifier, JSON.stringify(storedMessage));
	};
	
	ClientImpl.prototype.restore = function(key) {    	
		var value = localStorage.getItem(key);
		var storedMessage = JSON.parse(value);
		
		var wireMessage = new WireMessage(storedMessage.type, storedMessage);
		
		switch(storedMessage.type) {
		  case MESSAGE_TYPE.PUBLISH:
			  // Replace the payload message with a Message object.
			  var hex = storedMessage.payloadMessage.payloadHex;
			  var buffer = new ArrayBuffer((hex.length)/2);
			  var byteStream = new Uint8Array(buffer); 
			  var i = 0;
			  while (hex.length >= 2) { 
				  var x = parseInt(hex.substring(0, 2), 16);
				  hex = hex.substring(2, hex.length);
				  byteStream[i++] = x;
			  }
			  var payloadMessage = new Paho.MQTT.Message(byteStream);
			  
			  payloadMessage.qos = storedMessage.payloadMessage.qos;
			  payloadMessage.destinationName = storedMessage.payloadMessage.destinationName;
			  if (storedMessage.payloadMessage.duplicate) 
				  payloadMessage.duplicate = true;
			  if (storedMessage.payloadMessage.retained) 
				  payloadMessage.retained = true;	 
			  wireMessage.payloadMessage = payloadMessage;
			  
			  break;    
			  
			default:
			  throw Error(format(ERROR.INVALID_STORED_DATA, [key, value]));
		}
							
		if (key.indexOf("Sent:"+this._localKey) == 0) {
			wireMessage.payloadMessage.duplicate = true;
			this._sentMessages[wireMessage.messageIdentifier] = wireMessage;    		    
		} else if (key.indexOf("Received:"+this._localKey) == 0) {
			this._receivedMessages[wireMessage.messageIdentifier] = wireMessage;
		}
	};
	
	ClientImpl.prototype._process_queue = function () {
		var message = null;
		// Process messages in order they were added
		var fifo = this._msg_queue.reverse();

		// Send all queued messages down socket connection
		while ((message = fifo.pop())) {
			this._socket_send(message);
			// Notify listeners that message was successfully sent
			if (this._notify_msg_sent[message]) {
				this._notify_msg_sent[message]();
				delete this._notify_msg_sent[message];
			}
		}
	};

	/**
	 * Expect an ACK response for this message. Add message to the set of in progress
	 * messages and set an unused identifier in this message.
	 * @ignore
	 */
	ClientImpl.prototype._requires_ack = function (wireMessage) {
		var messageCount = Object.keys(this._sentMessages).length;
		if (messageCount > this.maxMessageIdentifier)
			throw Error ("Too many messages:"+messageCount);

		while(this._sentMessages[this._message_identifier] !== undefined) {
			this._message_identifier++;
		}
		wireMessage.messageIdentifier = this._message_identifier;
		this._sentMessages[wireMessage.messageIdentifier] = wireMessage;
		if (wireMessage.type === MESSAGE_TYPE.PUBLISH) {
			this.store("Sent:", wireMessage);
		}
		if (this._message_identifier === this.maxMessageIdentifier) {
			this._message_identifier = 1;
		}
	};

	/** 
	 * Called when the underlying websocket has been opened.
	 * @ignore
	 */
	ClientImpl.prototype._on_socket_open = function () {      
		// Create the CONNECT message object.
		var wireMessage = new WireMessage(MESSAGE_TYPE.CONNECT, this.connectOptions); 
		wireMessage.clientId = this.clientId;
		this._socket_send(wireMessage);
	};

	/** 
	 * Called when the underlying websocket has received a complete packet.
	 * @ignore
	 */
	ClientImpl.prototype._on_socket_message = function (event) {
		this._trace("Client._on_socket_message", event.data);
		// Reset the receive ping timer, we now have evidence the server is alive.
		this.receivePinger.reset();
		var messages = this._deframeMessages(event.data);
		if (messages == null) return;
		for (var i = 0; i < messages.length; i+=1) {
		    this._handleMessage(messages[i]);
		}
	}
	
	ClientImpl.prototype._deframeMessages = function(data) {
		var overflow = false;
		var byteArray = new Uint8Array(data);
	    if (this.receiveBuffer) {
	        var newData = new Uint8Array(this.receiveBuffer.length+byteArray.length);
	        newData.set(this.receiveBuffer);
	        newData.set(byteArray,this.receiveBuffer.length);
	        byteArray = newData;
	        delete this.receiveBuffer;
			overflow = true;
	    }
		try {
		    var offset = 0;
		    var messages = [];
		    while(offset < byteArray.length) {
		        var result = decodeMessage(byteArray,offset);
		        var wireMessage = result[0];
		        offset = result[1];
		        if (wireMessage !== null) {
		            messages.push(wireMessage);
		        } else {
		            break;
		        }
		    }
		    if (offset < byteArray.length) {
		    	this.receiveBuffer = byteArray.subarray(offset);
		    }
		} catch (error) {
			this._disconnected(ERROR.INTERNAL_ERROR.code , format(ERROR.INTERNAL_ERROR, [error.message,error.stack.toString()]));
			return;
		}
		return messages;
	}
	
	ClientImpl.prototype._handleMessage = function(wireMessage) {
		
		this._trace("Client._handleMessage", wireMessage);

		try {
			switch(wireMessage.type) {
			case MESSAGE_TYPE.CONNACK:
				this._connectTimeout.cancel();
				
				// If we have started using clean session then clear up the local state.
				if (this.connectOptions.cleanSession) {
					for (var key in this._sentMessages) {	    		
						var sentMessage = this._sentMessages[key];
						localStorage.removeItem("Sent:"+this._localKey+sentMessage.messageIdentifier);
					}
					this._sentMessages = {};

					for (var key in this._receivedMessages) {
						var receivedMessage = this._receivedMessages[key];
						localStorage.removeItem("Received:"+this._localKey+receivedMessage.messageIdentifier);
					}
					this._receivedMessages = {};
				}
				// Client connected and ready for business.
				if (wireMessage.returnCode === 0) {
					this.connected = true;
					// Jump to the end of the list of uris and stop looking for a good host.
					if (this.connectOptions.uris)
						this.hostIndex = this.connectOptions.uris.length;
				} else {
					this._disconnected(ERROR.CONNACK_RETURNCODE.code , format(ERROR.CONNACK_RETURNCODE, [wireMessage.returnCode, CONNACK_RC[wireMessage.returnCode]]));
					break;
				}
				
				// Resend messages.
				var sequencedMessages = new Array();
				for (var msgId in this._sentMessages) {
					if (this._sentMessages.hasOwnProperty(msgId))
						sequencedMessages.push(this._sentMessages[msgId]);
				}
		  
				// Sort sentMessages into the original sent order.
				var sequencedMessages = sequencedMessages.sort(function(a,b) {return a.sequence - b.sequence;} );
				for (var i=0, len=sequencedMessages.length; i<len; i++) {
					var sentMessage = sequencedMessages[i];
					if (sentMessage.type == MESSAGE_TYPE.PUBLISH && sentMessage.pubRecReceived) {
						var pubRelMessage = new WireMessage(MESSAGE_TYPE.PUBREL, {messageIdentifier:sentMessage.messageIdentifier});
						this._schedule_message(pubRelMessage);
					} else {
						this._schedule_message(sentMessage);
					};
				}

				// Execute the connectOptions.onSuccess callback if there is one.
				if (this.connectOptions.onSuccess) {
					this.connectOptions.onSuccess({invocationContext:this.connectOptions.invocationContext});
				}

				// Process all queued messages now that the connection is established. 
				this._process_queue();
				break;
		
			case MESSAGE_TYPE.PUBLISH:
				this._receivePublish(wireMessage);
				break;

			case MESSAGE_TYPE.PUBACK:
				var sentMessage = this._sentMessages[wireMessage.messageIdentifier];
				 // If this is a re flow of a PUBACK after we have restarted receivedMessage will not exist.
				if (sentMessage) {
					delete this._sentMessages[wireMessage.messageIdentifier];
					localStorage.removeItem("Sent:"+this._localKey+wireMessage.messageIdentifier);
					if (this.onMessageDelivered)
						this.onMessageDelivered(sentMessage.payloadMessage);
				}
				break;
			
			case MESSAGE_TYPE.PUBREC:
				var sentMessage = this._sentMessages[wireMessage.messageIdentifier];
				// If this is a re flow of a PUBREC after we have restarted receivedMessage will not exist.
				if (sentMessage) {
					sentMessage.pubRecReceived = true;
					var pubRelMessage = new WireMessage(MESSAGE_TYPE.PUBREL, {messageIdentifier:wireMessage.messageIdentifier});
					this.store("Sent:", sentMessage);
					this._schedule_message(pubRelMessage);
				}
				break;
								
			case MESSAGE_TYPE.PUBREL:
				var receivedMessage = this._receivedMessages[wireMessage.messageIdentifier];
				localStorage.removeItem("Received:"+this._localKey+wireMessage.messageIdentifier);
				// If this is a re flow of a PUBREL after we have restarted receivedMessage will not exist.
				if (receivedMessage) {
					this._receiveMessage(receivedMessage);
					delete this._receivedMessages[wireMessage.messageIdentifier];
				}
				// Always flow PubComp, we may have previously flowed PubComp but the server lost it and restarted.
				var pubCompMessage = new WireMessage(MESSAGE_TYPE.PUBCOMP, {messageIdentifier:wireMessage.messageIdentifier});
				this._schedule_message(pubCompMessage);                    
				break;

			case MESSAGE_TYPE.PUBCOMP: 
				var sentMessage = this._sentMessages[wireMessage.messageIdentifier];
				delete this._sentMessages[wireMessage.messageIdentifier];
				localStorage.removeItem("Sent:"+this._localKey+wireMessage.messageIdentifier);
				if (this.onMessageDelivered)
					this.onMessageDelivered(sentMessage.payloadMessage);
				break;
				
			case MESSAGE_TYPE.SUBACK:
				var sentMessage = this._sentMessages[wireMessage.messageIdentifier];
				if (sentMessage) {
					if(sentMessage.timeOut)
						sentMessage.timeOut.cancel();
					wireMessage.returnCode.indexOf = Array.prototype.indexOf;
					if (wireMessage.returnCode.indexOf(0x80) !== -1) {
						if (sentMessage.onFailure) {
							sentMessage.onFailure(wireMessage.returnCode);
						} 
					} else if (sentMessage.onSuccess) {
						sentMessage.onSuccess(wireMessage.returnCode);
					}
					delete this._sentMessages[wireMessage.messageIdentifier];
				}
				break;
				
			case MESSAGE_TYPE.UNSUBACK:
				var sentMessage = this._sentMessages[wireMessage.messageIdentifier];
				if (sentMessage) { 
					if (sentMessage.timeOut)
						sentMessage.timeOut.cancel();
					if (sentMessage.callback) {
						sentMessage.callback();
					}
					delete this._sentMessages[wireMessage.messageIdentifier];
				}

				break;
				
			case MESSAGE_TYPE.PINGRESP:
				/* The sendPinger or receivePinger may have sent a ping, the receivePinger has already been reset. */
				this.sendPinger.reset();
				break;
				
			case MESSAGE_TYPE.DISCONNECT:
				// Clients do not expect to receive disconnect packets.
				this._disconnected(ERROR.INVALID_MQTT_MESSAGE_TYPE.code , format(ERROR.INVALID_MQTT_MESSAGE_TYPE, [wireMessage.type]));
				break;

			default:
				this._disconnected(ERROR.INVALID_MQTT_MESSAGE_TYPE.code , format(ERROR.INVALID_MQTT_MESSAGE_TYPE, [wireMessage.type]));
			};
		} catch (error) {
			this._disconnected(ERROR.INTERNAL_ERROR.code , format(ERROR.INTERNAL_ERROR, [error.message,error.stack.toString()]));
			return;
		}
	};
	
	/** @ignore */
	ClientImpl.prototype._on_socket_error = function (error) {
		this._disconnected(ERROR.SOCKET_ERROR.code , format(ERROR.SOCKET_ERROR, [error.data]));
	};

	/** @ignore */
	ClientImpl.prototype._on_socket_close = function () {
		this._disconnected(ERROR.SOCKET_CLOSE.code , format(ERROR.SOCKET_CLOSE));
	};

	/** @ignore */
	ClientImpl.prototype._socket_send = function (wireMessage) {
		
		if (wireMessage.type == 1) {
			var wireMessageMasked = this._traceMask(wireMessage, "password"); 
			this._trace("Client._socket_send", wireMessageMasked);
		}
		else this._trace("Client._socket_send", wireMessage);
		
		this.socket.send(wireMessage.encode());
		/* We have proved to the server we are alive. */
		this.sendPinger.reset();
	};
	
	/** @ignore */
	ClientImpl.prototype._receivePublish = function (wireMessage) {
		switch(wireMessage.payloadMessage.qos) {
			case "undefined":
			case 0:
				this._receiveMessage(wireMessage);
				break;

			case 1:
				var pubAckMessage = new WireMessage(MESSAGE_TYPE.PUBACK, {messageIdentifier:wireMessage.messageIdentifier});
				this._schedule_message(pubAckMessage);
				this._receiveMessage(wireMessage);
				break;

			case 2:
				this._receivedMessages[wireMessage.messageIdentifier] = wireMessage;
				this.store("Received:", wireMessage);
				var pubRecMessage = new WireMessage(MESSAGE_TYPE.PUBREC, {messageIdentifier:wireMessage.messageIdentifier});
				this._schedule_message(pubRecMessage);

				break;

			default:
				throw Error("Invaild qos="+wireMmessage.payloadMessage.qos);
		};
	};

	/** @ignore */
	ClientImpl.prototype._receiveMessage = function (wireMessage) {
		if (this.onMessageArrived) {
			this.onMessageArrived(wireMessage.payloadMessage);
		}
	};

	/**
	 * Client has disconnected either at its own request or because the server
	 * or network disconnected it. Remove all non-durable state.
	 * @param {errorCode} [number] the error number.
	 * @param {errorText} [string] the error text.
	 * @ignore
	 */
	ClientImpl.prototype._disconnected = function (errorCode, errorText) {
		this._trace("Client._disconnected", errorCode, errorText);
		
		this.sendPinger.cancel();
		this.receivePinger.cancel();
		if (this._connectTimeout)
			this._connectTimeout.cancel();
		// Clear message buffers.
		this._msg_queue = [];
		this._notify_msg_sent = {};
	   
		if (this.socket) {
			// Cancel all socket callbacks so that they cannot be driven again by this socket.
			this.socket.onopen = null;
			this.socket.onmessage = null;
			this.socket.onerror = null;
			this.socket.onclose = null;
			if (this.socket.readyState === 1)
				this.socket.close();
			delete this.socket;           
		}
		
		if (this.connectOptions.uris && this.hostIndex < this.connectOptions.uris.length-1) {
			// Try the next host.
			this.hostIndex++;
			this._doConnect(this.connectOptions.uris[this.hostIndex]);
		
		} else {
		
			if (errorCode === undefined) {
				errorCode = ERROR.OK.code;
				errorText = format(ERROR.OK);
			}
			
			// Run any application callbacks last as they may attempt to reconnect and hence create a new socket.
			if (this.connected) {
				this.connected = false;
				// Execute the connectionLostCallback if there is one, and we were connected.       
				if (this.onConnectionLost)
					this.onConnectionLost({errorCode:errorCode, errorMessage:errorText});      	
			} else {
				// Otherwise we never had a connection, so indicate that the connect has failed.
				if (this.connectOptions.mqttVersion === 4 && this.connectOptions.mqttVersionExplicit === false) {
					this._trace("Failed to connect V4, dropping back to V3")
					this.connectOptions.mqttVersion = 3;
					if (this.connectOptions.uris) {
						this.hostIndex = 0;
						this._doConnect(this.connectOptions.uris[0]);  
					} else {
						this._doConnect(this.uri);
					}	
				} else if(this.connectOptions.onFailure) {
					this.connectOptions.onFailure({invocationContext:this.connectOptions.invocationContext, errorCode:errorCode, errorMessage:errorText});
				}
			}
		}
	};

	/** @ignore */
	ClientImpl.prototype._trace = function () {
		// Pass trace message back to client's callback function
		if (this.traceFunction) {
			for (var i in arguments)
			{	
				if (typeof arguments[i] !== "undefined")
					arguments[i] = JSON.stringify(arguments[i]);
			}
			var record = Array.prototype.slice.call(arguments).join("");
			this.traceFunction ({severity: "Debug", message: record	});
		}

		//buffer style trace
		if ( this._traceBuffer !== null ) {  
			for (var i = 0, max = arguments.length; i < max; i++) {
				if ( this._traceBuffer.length == this._MAX_TRACE_ENTRIES ) {    
					this._traceBuffer.shift();              
				}
				if (i === 0) this._traceBuffer.push(arguments[i]);
				else if (typeof arguments[i] === "undefined" ) this._traceBuffer.push(arguments[i]);
				else this._traceBuffer.push("  "+JSON.stringify(arguments[i]));
		   };
		};
	};
	
	/** @ignore */
	ClientImpl.prototype._traceMask = function (traceObject, masked) {
		var traceObjectMasked = {};
		for (var attr in traceObject) {
			if (traceObject.hasOwnProperty(attr)) {
				if (attr == masked) 
					traceObjectMasked[attr] = "******";
				else
					traceObjectMasked[attr] = traceObject[attr];
			} 
		}
		return traceObjectMasked;
	};

	// ------------------------------------------------------------------------
	// Public Programming interface.
	// ------------------------------------------------------------------------
	
	/** 
	 * The JavaScript application communicates to the server using a {@link Paho.MQTT.Client} object. 
	 * <p>
	 * Most applications will create just one Client object and then call its connect() method,
	 * however applications can create more than one Client object if they wish. 
	 * In this case the combination of host, port and clientId attributes must be different for each Client object.
	 * <p>
	 * The send, subscribe and unsubscribe methods are implemented as asynchronous JavaScript methods 
	 * (even though the underlying protocol exchange might be synchronous in nature). 
	 * This means they signal their completion by calling back to the application, 
	 * via Success or Failure callback functions provided by the application on the method in question. 
	 * Such callbacks are called at most once per method invocation and do not persist beyond the lifetime 
	 * of the script that made the invocation.
	 * <p>
	 * In contrast there are some callback functions, most notably <i>onMessageArrived</i>, 
	 * that are defined on the {@link Paho.MQTT.Client} object.  
	 * These may get called multiple times, and aren't directly related to specific method invocations made by the client. 
	 *
	 * @name Paho.MQTT.Client    
	 * 
	 * @constructor
	 *  
	 * @param {string} host - the address of the messaging server, as a fully qualified WebSocket URI, as a DNS name or dotted decimal IP address.
	 * @param {number} port - the port number to connect to - only required if host is not a URI
	 * @param {string} path - the path on the host to connect to - only used if host is not a URI. Default: '/mqtt'.
	 * @param {string} clientId - the Messaging client identifier, between 1 and 23 characters in length.
	 * 
	 * @property {string} host - <i>read only</i> the server's DNS hostname or dotted decimal IP address.
	 * @property {number} port - <i>read only</i> the server's port.
	 * @property {string} path - <i>read only</i> the server's path.
	 * @property {string} clientId - <i>read only</i> used when connecting to the server.
	 * @property {function} onConnectionLost - called when a connection has been lost. 
	 *                            after a connect() method has succeeded.
	 *                            Establish the call back used when a connection has been lost. The connection may be
	 *                            lost because the client initiates a disconnect or because the server or network 
	 *                            cause the client to be disconnected. The disconnect call back may be called without 
	 *                            the connectionComplete call back being invoked if, for example the client fails to 
	 *                            connect.
	 *                            A single response object parameter is passed to the onConnectionLost callback containing the following fields:
	 *                            <ol>   
	 *                            <li>errorCode
	 *                            <li>errorMessage       
	 *                            </ol>
	 * @property {function} onMessageDelivered called when a message has been delivered. 
	 *                            All processing that this Client will ever do has been completed. So, for example,
	 *                            in the case of a Qos=2 message sent by this client, the PubComp flow has been received from the server
	 *                            and the message has been removed from persistent storage before this callback is invoked. 
	 *                            Parameters passed to the onMessageDelivered callback are:
	 *                            <ol>   
	 *                            <li>{@link Paho.MQTT.Message} that was delivered.
	 *                            </ol>    
	 * @property {function} onMessageArrived called when a message has arrived in this Paho.MQTT.client. 
	 *                            Parameters passed to the onMessageArrived callback are:
	 *                            <ol>   
	 *                            <li>{@link Paho.MQTT.Message} that has arrived.
	 *                            </ol>    
	 */
	var Client = function (host, port, path, clientId) {
	    
	    var uri;
	    
		if (typeof host !== "string")
			throw new Error(format(ERROR.INVALID_TYPE, [typeof host, "host"]));
	    
	    if (arguments.length == 2) {
	        // host: must be full ws:// uri
	        // port: clientId
	        clientId = port;
	        uri = host;
	        var match = uri.match(/^(wss?):\/\/((\[(.+)\])|([^\/]+?))(:(\d+))?(\/.*)$/);
	        if (match) {
	            host = match[4]||match[2];
	            port = parseInt(match[7]);
	            path = match[8];
	        } else {
	            throw new Error(format(ERROR.INVALID_ARGUMENT,[host,"host"]));
	        }
	    } else {
	        if (arguments.length == 3) {
				clientId = path;
				path = "/mqtt";
			}
			if (typeof port !== "number" || port < 0)
				throw new Error(format(ERROR.INVALID_TYPE, [typeof port, "port"]));
			if (typeof path !== "string")
				throw new Error(format(ERROR.INVALID_TYPE, [typeof path, "path"]));
			
			var ipv6AddSBracket = (host.indexOf(":") != -1 && host.slice(0,1) != "[" && host.slice(-1) != "]");
			uri = "ws://"+(ipv6AddSBracket?"["+host+"]":host)+":"+port+path;
		}

		var clientIdLength = 0;
		for (var i = 0; i<clientId.length; i++) {
			var charCode = clientId.charCodeAt(i);                   
			if (0xD800 <= charCode && charCode <= 0xDBFF)  {    			
				 i++; // Surrogate pair.
			}   		   
			clientIdLength++;
		}     	   	
		if (typeof clientId !== "string" || clientIdLength > 65535)
			throw new Error(format(ERROR.INVALID_ARGUMENT, [clientId, "clientId"])); 
		
		var client = new ClientImpl(uri, host, port, path, clientId);
		this._getHost =  function() { return host; };
		this._setHost = function() { throw new Error(format(ERROR.UNSUPPORTED_OPERATION)); };
			
		this._getPort = function() { return port; };
		this._setPort = function() { throw new Error(format(ERROR.UNSUPPORTED_OPERATION)); };

		this._getPath = function() { return path; };
		this._setPath = function() { throw new Error(format(ERROR.UNSUPPORTED_OPERATION)); };

		this._getURI = function() { return uri; };
		this._setURI = function() { throw new Error(format(ERROR.UNSUPPORTED_OPERATION)); };
		
		this._getClientId = function() { return client.clientId; };
		this._setClientId = function() { throw new Error(format(ERROR.UNSUPPORTED_OPERATION)); };
		
		this._getOnConnectionLost = function() { return client.onConnectionLost; };
		this._setOnConnectionLost = function(newOnConnectionLost) { 
			if (typeof newOnConnectionLost === "function")
				client.onConnectionLost = newOnConnectionLost;
			else 
				throw new Error(format(ERROR.INVALID_TYPE, [typeof newOnConnectionLost, "onConnectionLost"]));
		};

		this._getOnMessageDelivered = function() { return client.onMessageDelivered; };
		this._setOnMessageDelivered = function(newOnMessageDelivered) { 
			if (typeof newOnMessageDelivered === "function")
				client.onMessageDelivered = newOnMessageDelivered;
			else 
				throw new Error(format(ERROR.INVALID_TYPE, [typeof newOnMessageDelivered, "onMessageDelivered"]));
		};
	   
		this._getOnMessageArrived = function() { return client.onMessageArrived; };
		this._setOnMessageArrived = function(newOnMessageArrived) { 
			if (typeof newOnMessageArrived === "function")
				client.onMessageArrived = newOnMessageArrived;
			else 
				throw new Error(format(ERROR.INVALID_TYPE, [typeof newOnMessageArrived, "onMessageArrived"]));
		};

		this._getTrace = function() { return client.traceFunction; };
		this._setTrace = function(trace) {
			if(typeof trace === "function"){
				client.traceFunction = trace;
			}else{
				throw new Error(format(ERROR.INVALID_TYPE, [typeof trace, "onTrace"]));
			}
		};
		
		/** 
		 * Connect this Messaging client to its server. 
		 * 
		 * @name Paho.MQTT.Client#connect
		 * @function
		 * @param {Object} connectOptions - attributes used with the connection. 
		 * @param {number} connectOptions.timeout - If the connect has not succeeded within this 
		 *                    number of seconds, it is deemed to have failed.
		 *                    The default is 30 seconds.
		 * @param {string} connectOptions.userName - Authentication username for this connection.
		 * @param {string} connectOptions.password - Authentication password for this connection.
		 * @param {Paho.MQTT.Message} connectOptions.willMessage - sent by the server when the client
		 *                    disconnects abnormally.
		 * @param {Number} connectOptions.keepAliveInterval - the server disconnects this client if
		 *                    there is no activity for this number of seconds.
		 *                    The default value of 60 seconds is assumed if not set.
		 * @param {boolean} connectOptions.cleanSession - if true(default) the client and server 
		 *                    persistent state is deleted on successful connect.
		 * @param {boolean} connectOptions.useSSL - if present and true, use an SSL Websocket connection.
		 * @param {object} connectOptions.invocationContext - passed to the onSuccess callback or onFailure callback.
		 * @param {function} connectOptions.onSuccess - called when the connect acknowledgement 
		 *                    has been received from the server.
		 * A single response object parameter is passed to the onSuccess callback containing the following fields:
		 * <ol>
		 * <li>invocationContext as passed in to the onSuccess method in the connectOptions.       
		 * </ol>
		 * @config {function} [onFailure] called when the connect request has failed or timed out.
		 * A single response object parameter is passed to the onFailure callback containing the following fields:
		 * <ol>
		 * <li>invocationContext as passed in to the onFailure method in the connectOptions.       
		 * <li>errorCode a number indicating the nature of the error.
		 * <li>errorMessage text describing the error.      
		 * </ol>
		 * @config {Array} [hosts] If present this contains either a set of hostnames or fully qualified
		 * WebSocket URIs (ws://example.com:1883/mqtt), that are tried in order in place 
		 * of the host and port paramater on the construtor. The hosts are tried one at at time in order until
		 * one of then succeeds.
		 * @config {Array} [ports] If present the set of ports matching the hosts. If hosts contains URIs, this property
		 * is not used.
		 * @throws {InvalidState} if the client is not in disconnected state. The client must have received connectionLost
		 * or disconnected before calling connect for a second or subsequent time.
		 */
		this.connect = function (connectOptions) {
			connectOptions = connectOptions || {} ;
			validate(connectOptions,  {timeout:"number",
									   userName:"string", 
									   password:"string", 
									   willMessage:"object", 
									   keepAliveInterval:"number", 
									   cleanSession:"boolean", 
									   useSSL:"boolean",
									   invocationContext:"object", 
									   onSuccess:"function", 
									   onFailure:"function",
									   hosts:"object",
									   ports:"object",
									   mqttVersion:"number"});
			
			// If no keep alive interval is set, assume 60 seconds.
			if (connectOptions.keepAliveInterval === undefined)
				connectOptions.keepAliveInterval = 60;

			if (connectOptions.mqttVersion > 4 || connectOptions.mqttVersion < 3) {
				throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.mqttVersion, "connectOptions.mqttVersion"]));
			}

			if (connectOptions.mqttVersion === undefined) {
				connectOptions.mqttVersionExplicit = false;
				connectOptions.mqttVersion = 4;
			} else {
				connectOptions.mqttVersionExplicit = true;
			}

			//Check that if password is set, so is username
			if (connectOptions.password === undefined && connectOptions.userName !== undefined)
				throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.password, "connectOptions.password"]))

			if (connectOptions.willMessage) {
				if (!(connectOptions.willMessage instanceof Message))
					throw new Error(format(ERROR.INVALID_TYPE, [connectOptions.willMessage, "connectOptions.willMessage"]));
				// The will message must have a payload that can be represented as a string.
				// Cause the willMessage to throw an exception if this is not the case.
				connectOptions.willMessage.stringPayload;
				
				if (typeof connectOptions.willMessage.destinationName === "undefined")
					throw new Error(format(ERROR.INVALID_TYPE, [typeof connectOptions.willMessage.destinationName, "connectOptions.willMessage.destinationName"]));
			}
			if (typeof connectOptions.cleanSession === "undefined")
				connectOptions.cleanSession = true;
			if (connectOptions.hosts) {
			    
				if (!(connectOptions.hosts instanceof Array) )
					throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.hosts, "connectOptions.hosts"]));
				if (connectOptions.hosts.length <1 )
					throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.hosts, "connectOptions.hosts"]));
				
				var usingURIs = false;
				for (var i = 0; i<connectOptions.hosts.length; i++) {
					if (typeof connectOptions.hosts[i] !== "string")
						throw new Error(format(ERROR.INVALID_TYPE, [typeof connectOptions.hosts[i], "connectOptions.hosts["+i+"]"]));
					if (/^(wss?):\/\/((\[(.+)\])|([^\/]+?))(:(\d+))?(\/.*)$/.test(connectOptions.hosts[i])) {
						if (i == 0) {
							usingURIs = true;
						} else if (!usingURIs) {
							throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.hosts[i], "connectOptions.hosts["+i+"]"]));
						}
					} else if (usingURIs) {
						throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.hosts[i], "connectOptions.hosts["+i+"]"]));
					}
				}
				
				if (!usingURIs) {
					if (!connectOptions.ports)
						throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.ports, "connectOptions.ports"]));
					if (!(connectOptions.ports instanceof Array) )
						throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.ports, "connectOptions.ports"]));
					if (connectOptions.hosts.length != connectOptions.ports.length)
						throw new Error(format(ERROR.INVALID_ARGUMENT, [connectOptions.ports, "connectOptions.ports"]));
					
					connectOptions.uris = [];
					
					for (var i = 0; i<connectOptions.hosts.length; i++) {
						if (typeof connectOptions.ports[i] !== "number" || connectOptions.ports[i] < 0)
							throw new Error(format(ERROR.INVALID_TYPE, [typeof connectOptions.ports[i], "connectOptions.ports["+i+"]"]));
						var host = connectOptions.hosts[i];
						var port = connectOptions.ports[i];
						
						var ipv6 = (host.indexOf(":") != -1);
						uri = "ws://"+(ipv6?"["+host+"]":host)+":"+port+path;
						connectOptions.uris.push(uri);
					}
				} else {
					connectOptions.uris = connectOptions.hosts;
				}
			}
			
			client.connect(connectOptions);
		};
	 
		/** 
		 * Subscribe for messages, request receipt of a copy of messages sent to the destinations described by the filter.
		 * 
		 * @name Paho.MQTT.Client#subscribe
		 * @function
		 * @param {string} filter describing the destinations to receive messages from.
		 * <br>
		 * @param {object} subscribeOptions - used to control the subscription
		 *
		 * @param {number} subscribeOptions.qos - the maiximum qos of any publications sent 
		 *                                  as a result of making this subscription.
		 * @param {object} subscribeOptions.invocationContext - passed to the onSuccess callback 
		 *                                  or onFailure callback.
		 * @param {function} subscribeOptions.onSuccess - called when the subscribe acknowledgement
		 *                                  has been received from the server.
		 *                                  A single response object parameter is passed to the onSuccess callback containing the following fields:
		 *                                  <ol>
		 *                                  <li>invocationContext if set in the subscribeOptions.       
		 *                                  </ol>
		 * @param {function} subscribeOptions.onFailure - called when the subscribe request has failed or timed out.
		 *                                  A single response object parameter is passed to the onFailure callback containing the following fields:
		 *                                  <ol>
		 *                                  <li>invocationContext - if set in the subscribeOptions.       
		 *                                  <li>errorCode - a number indicating the nature of the error.
		 *                                  <li>errorMessage - text describing the error.      
		 *                                  </ol>
		 * @param {number} subscribeOptions.timeout - which, if present, determines the number of
		 *                                  seconds after which the onFailure calback is called.
		 *                                  The presence of a timeout does not prevent the onSuccess
		 *                                  callback from being called when the subscribe completes.         
		 * @throws {InvalidState} if the client is not in connected state.
		 */
		this.subscribe = function (filter, subscribeOptions) {
			if (typeof filter !== "string")
				throw new Error("Invalid argument:"+filter);
			subscribeOptions = subscribeOptions || {} ;
			validate(subscribeOptions,  {qos:"number", 
										 invocationContext:"object", 
										 onSuccess:"function", 
										 onFailure:"function",
										 timeout:"number"
										});
			if (subscribeOptions.timeout && !subscribeOptions.onFailure)
				throw new Error("subscribeOptions.timeout specified with no onFailure callback.");
			if (typeof subscribeOptions.qos !== "undefined" 
				&& !(subscribeOptions.qos === 0 || subscribeOptions.qos === 1 || subscribeOptions.qos === 2 ))
				throw new Error(format(ERROR.INVALID_ARGUMENT, [subscribeOptions.qos, "subscribeOptions.qos"]));
			client.subscribe(filter, subscribeOptions);
		};

		/**
		 * Unsubscribe for messages, stop receiving messages sent to destinations described by the filter.
		 * 
		 * @name Paho.MQTT.Client#unsubscribe
		 * @function
		 * @param {string} filter - describing the destinations to receive messages from.
		 * @param {object} unsubscribeOptions - used to control the subscription
		 * @param {object} unsubscribeOptions.invocationContext - passed to the onSuccess callback 
		                                      or onFailure callback.
		 * @param {function} unsubscribeOptions.onSuccess - called when the unsubscribe acknowledgement has been received from the server.
		 *                                    A single response object parameter is passed to the 
		 *                                    onSuccess callback containing the following fields:
		 *                                    <ol>
		 *                                    <li>invocationContext - if set in the unsubscribeOptions.     
		 *                                    </ol>
		 * @param {function} unsubscribeOptions.onFailure called when the unsubscribe request has failed or timed out.
		 *                                    A single response object parameter is passed to the onFailure callback containing the following fields:
		 *                                    <ol>
		 *                                    <li>invocationContext - if set in the unsubscribeOptions.       
		 *                                    <li>errorCode - a number indicating the nature of the error.
		 *                                    <li>errorMessage - text describing the error.      
		 *                                    </ol>
		 * @param {number} unsubscribeOptions.timeout - which, if present, determines the number of seconds
		 *                                    after which the onFailure callback is called. The presence of
		 *                                    a timeout does not prevent the onSuccess callback from being
		 *                                    called when the unsubscribe completes
		 * @throws {InvalidState} if the client is not in connected state.
		 */
		this.unsubscribe = function (filter, unsubscribeOptions) {
			if (typeof filter !== "string")
				throw new Error("Invalid argument:"+filter);
			unsubscribeOptions = unsubscribeOptions || {} ;
			validate(unsubscribeOptions,  {invocationContext:"object", 
										   onSuccess:"function", 
										   onFailure:"function",
										   timeout:"number"
										  });
			if (unsubscribeOptions.timeout && !unsubscribeOptions.onFailure)
				throw new Error("unsubscribeOptions.timeout specified with no onFailure callback.");
			client.unsubscribe(filter, unsubscribeOptions);
		};

		/**
		 * Send a message to the consumers of the destination in the Message.
		 * 
		 * @name Paho.MQTT.Client#send
		 * @function 
		 * @param {string|Paho.MQTT.Message} topic - <b>mandatory</b> The name of the destination to which the message is to be sent. 
		 * 					   - If it is the only parameter, used as Paho.MQTT.Message object.
		 * @param {String|ArrayBuffer} payload - The message data to be sent. 
		 * @param {number} qos The Quality of Service used to deliver the message.
		 * 		<dl>
		 * 			<dt>0 Best effort (default).
		 *     			<dt>1 At least once.
		 *     			<dt>2 Exactly once.     
		 * 		</dl>
		 * @param {Boolean} retained If true, the message is to be retained by the server and delivered 
		 *                     to both current and future subscriptions.
		 *                     If false the server only delivers the message to current subscribers, this is the default for new Messages. 
		 *                     A received message has the retained boolean set to true if the message was published 
		 *                     with the retained boolean set to true
		 *                     and the subscrption was made after the message has been published. 
		 * @throws {InvalidState} if the client is not connected.
		 */   
		this.send = function (topic,payload,qos,retained) {   
			var message ;  
			
			if(arguments.length == 0){
				throw new Error("Invalid argument."+"length");

			}else if(arguments.length == 1) {

				if (!(topic instanceof Message) && (typeof topic !== "string"))
					throw new Error("Invalid argument:"+ typeof topic);

				message = topic;
				if (typeof message.destinationName === "undefined")
					throw new Error(format(ERROR.INVALID_ARGUMENT,[message.destinationName,"Message.destinationName"]));
				client.send(message); 

			}else {
				//parameter checking in Message object 
				message = new Message(payload);
				message.destinationName = topic;
				if(arguments.length >= 3)
					message.qos = qos;
				if(arguments.length >= 4)
					message.retained = retained;
				client.send(message); 
			}
		};
		
		/** 
		 * Normal disconnect of this Messaging client from its server.
		 * 
		 * @name Paho.MQTT.Client#disconnect
		 * @function
		 * @throws {InvalidState} if the client is already disconnected.     
		 */
		this.disconnect = function () {
			client.disconnect();
		};
		
		/** 
		 * Get the contents of the trace log.
		 * 
		 * @name Paho.MQTT.Client#getTraceLog
		 * @function
		 * @return {Object[]} tracebuffer containing the time ordered trace records.
		 */
		this.getTraceLog = function () {
			return client.getTraceLog();
		}
		
		/** 
		 * Start tracing.
		 * 
		 * @name Paho.MQTT.Client#startTrace
		 * @function
		 */
		this.startTrace = function () {
			client.startTrace();
		};
		
		/** 
		 * Stop tracing.
		 * 
		 * @name Paho.MQTT.Client#stopTrace
		 * @function
		 */
		this.stopTrace = function () {
			client.stopTrace();
		};

		this.isConnected = function() {
			return client.connected;
		};
	};

	Client.prototype = {
		get host() { return this._getHost(); },
		set host(newHost) { this._setHost(newHost); },
			
		get port() { return this._getPort(); },
		set port(newPort) { this._setPort(newPort); },

		get path() { return this._getPath(); },
		set path(newPath) { this._setPath(newPath); },
			
		get clientId() { return this._getClientId(); },
		set clientId(newClientId) { this._setClientId(newClientId); },

		get onConnectionLost() { return this._getOnConnectionLost(); },
		set onConnectionLost(newOnConnectionLost) { this._setOnConnectionLost(newOnConnectionLost); },

		get onMessageDelivered() { return this._getOnMessageDelivered(); },
		set onMessageDelivered(newOnMessageDelivered) { this._setOnMessageDelivered(newOnMessageDelivered); },
		
		get onMessageArrived() { return this._getOnMessageArrived(); },
		set onMessageArrived(newOnMessageArrived) { this._setOnMessageArrived(newOnMessageArrived); },

		get trace() { return this._getTrace(); },
		set trace(newTraceFunction) { this._setTrace(newTraceFunction); }	

	};
	
	/** 
	 * An application message, sent or received.
	 * <p>
	 * All attributes may be null, which implies the default values.
	 * 
	 * @name Paho.MQTT.Message
	 * @constructor
	 * @param {String|ArrayBuffer} payload The message data to be sent.
	 * <p>
	 * @property {string} payloadString <i>read only</i> The payload as a string if the payload consists of valid UTF-8 characters.
	 * @property {ArrayBuffer} payloadBytes <i>read only</i> The payload as an ArrayBuffer.
	 * <p>
	 * @property {string} destinationName <b>mandatory</b> The name of the destination to which the message is to be sent
	 *                    (for messages about to be sent) or the name of the destination from which the message has been received.
	 *                    (for messages received by the onMessage function).
	 * <p>
	 * @property {number} qos The Quality of Service used to deliver the message.
	 * <dl>
	 *     <dt>0 Best effort (default).
	 *     <dt>1 At least once.
	 *     <dt>2 Exactly once.     
	 * </dl>
	 * <p>
	 * @property {Boolean} retained If true, the message is to be retained by the server and delivered 
	 *                     to both current and future subscriptions.
	 *                     If false the server only delivers the message to current subscribers, this is the default for new Messages. 
	 *                     A received message has the retained boolean set to true if the message was published 
	 *                     with the retained boolean set to true
	 *                     and the subscrption was made after the message has been published. 
	 * <p>
	 * @property {Boolean} duplicate <i>read only</i> If true, this message might be a duplicate of one which has already been received. 
	 *                     This is only set on messages received from the server.
	 *                     
	 */
	var Message = function (newPayload) {  
		var payload;
		if (   typeof newPayload === "string" 
			|| newPayload instanceof ArrayBuffer
			|| newPayload instanceof Int8Array
			|| newPayload instanceof Uint8Array
			|| newPayload instanceof Int16Array
			|| newPayload instanceof Uint16Array
			|| newPayload instanceof Int32Array
			|| newPayload instanceof Uint32Array
			|| newPayload instanceof Float32Array
			|| newPayload instanceof Float64Array
		   ) {
			payload = newPayload;
		} else {
			throw (format(ERROR.INVALID_ARGUMENT, [newPayload, "newPayload"]));
		}

		this._getPayloadString = function () {
			if (typeof payload === "string")
				return payload;
			else
				return parseUTF8(payload, 0, payload.length); 
		};

		this._getPayloadBytes = function() {
			if (typeof payload === "string") {
				var buffer = new ArrayBuffer(UTF8Length(payload));
				var byteStream = new Uint8Array(buffer); 
				stringToUTF8(payload, byteStream, 0);

				return byteStream;
			} else {
				return payload;
			};
		};

		var destinationName = undefined;
		this._getDestinationName = function() { return destinationName; };
		this._setDestinationName = function(newDestinationName) { 
			if (typeof newDestinationName === "string")
				destinationName = newDestinationName;
			else 
				throw new Error(format(ERROR.INVALID_ARGUMENT, [newDestinationName, "newDestinationName"]));
		};
				
		var qos = 0;
		this._getQos = function() { return qos; };
		this._setQos = function(newQos) { 
			if (newQos === 0 || newQos === 1 || newQos === 2 )
				qos = newQos;
			else 
				throw new Error("Invalid argument:"+newQos);
		};

		var retained = false;
		this._getRetained = function() { return retained; };
		this._setRetained = function(newRetained) { 
			if (typeof newRetained === "boolean")
				retained = newRetained;
			else 
				throw new Error(format(ERROR.INVALID_ARGUMENT, [newRetained, "newRetained"]));
		};
		
		var duplicate = false;
		this._getDuplicate = function() { return duplicate; };
		this._setDuplicate = function(newDuplicate) { duplicate = newDuplicate; };
	};
	
	Message.prototype = {
		get payloadString() { return this._getPayloadString(); },
		get payloadBytes() { return this._getPayloadBytes(); },
		
		get destinationName() { return this._getDestinationName(); },
		set destinationName(newDestinationName) { this._setDestinationName(newDestinationName); },
		
		get qos() { return this._getQos(); },
		set qos(newQos) { this._setQos(newQos); },

		get retained() { return this._getRetained(); },
		set retained(newRetained) { this._setRetained(newRetained); },

		get duplicate() { return this._getDuplicate(); },
		set duplicate(newDuplicate) { this._setDuplicate(newDuplicate); }
	};
	   
	// Module contents.
	return {
		Client: Client,
		Message: Message
	};
})(window);
